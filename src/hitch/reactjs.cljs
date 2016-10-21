(ns hitch.reactjs
  (:require cljsjs.react
            cljsjs.react.dom
            [hitch.protocol :as proto]
            [hitch.graph :as graph]
            [cljs.core.async :as async]
            [clojure.set]
            [goog.async.nextTick]
            [cljs.core.async.impl.protocols :as impl]
            [hitch.oldprotocols :as oldproto]))

(def ^not-native batchedUpdates
  "The React batchUpdates addon. Takes one function which should call
  forceUpdate (possibly multiple times) inside it. This function will be called
  later and all components updated will have their forceUpdates reordered and
  applied in a single React transaction."
  js/ReactDOM.unstable_batchedUpdates)



(def ^:dynamic react-read-mode nil)
(defonce invalidated-components (atom (transient #{})))
(def queued? false)
(defonce selector->component (atom {}))

(defn subscriber-notify! []
  (let [components (persistent! @invalidated-components)]
    (reset! invalidated-components (transient #{}))
    (set! queued? false)
    (doseq [component components]
      ;; Could be multimethod/protocol instead (e.g.: -receive-subscription-update)?
      (assert (not (undefined? (.-isMounted component))))
      (when (.isMounted component)                                  ;; react component
        (.forceUpdate component)))))

(defn flush-invalidated! []
   (batchedUpdates subscriber-notify!))

(defn monkeypatch-change-notify-on-all-react! []
  (extend-type js/React.Component
    oldproto/ExternalDependent
    (-change-notify [this]
      (when-not react-read-mode
        (when-not queued?
          (set! queued? true)
          (goog.async.nextTick flush-invalidated!))
        (swap! invalidated-components conj! this)))))

(deftype ReactHitcher [graph react-component
                       ^:mutable old-requests
                       ^:mutable not-requested
                       ^:mutable new-requests
                       ^:mutable all-requests]
  ILookup
  (-lookup [o data-selector]
    (-lookup o data-selector nil))
  (-lookup [o data-selector not-found]
    (-lookup graph data-selector not-found))
  oldproto/IDependTrack
  (dget-sel! [this data-selector nf]
    (if (old-requests data-selector)
      (set! not-requested (disj! not-requested data-selector))
      (set! new-requests (conj! new-requests data-selector)))
    (set! all-requests (conj! all-requests data-selector))
    (let [v (get this data-selector oldproto/NOT-IN-GRAPH-SENTINEL)]
      (if (identical? v oldproto/NOT-IN-GRAPH-SENTINEL)
        (if (satisfies? oldproto/IEagerSelectorResolve graph)
          (oldproto/attempt-eager-selector-resolution! graph data-selector nf)
          nf)
        v) ))
  (get-depends [this] all-requests)
  oldproto/IDependencyGraph
  (apply-commands [_ selector-command-pairs]
    (oldproto/apply-commands graph selector-command-pairs))
  oldproto/ITXManager
  (enqueue-dependency-changes [this]
    (let [removed-deps not-requested]
      (oldproto/update-parents graph react-component new-requests not-requested)
      (let [new-old (reduce disj!
                            (transient (into old-requests new-requests))
                            not-requested)]

        (set! old-requests (persistent! new-old))
        (set! not-requested new-old)
        (set! new-requests (transient #{}))
        (set! all-requests (transient #{})))
      removed-deps
      )))


(defn react-hitcher [graph react-component olddeps]
  (->ReactHitcher graph react-component olddeps (transient olddeps) (transient #{}) (transient #{})))
