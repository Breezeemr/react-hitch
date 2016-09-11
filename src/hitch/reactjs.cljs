(ns hitch.reactjs
  (:require cljsjs.react
            cljsjs.react.dom
            [hitch.protocols :as proto]
            [hitch.graph :as graph]
            [cljs.core.async :as async]
            [clojure.set]
            [goog.async.nextTick]
            [cljs.core.async.impl.protocols :as impl]))

(def ^not-native batchedUpdates
  "The React batchUpdates addon. Takes one function which should call
  forceUpdate (possibly multiple times) inside it. This function will be called
  later and all components updated will have their forceUpdates reordered and
  applied in a single React transaction."
  js/ReactDOM.unstable_batchedUpdates)




(defonce invalidated-selectors (atom (transient #{})))
(def queued? false)
(defonce selector->component (atom {}))

(defn subscriber-notify! []
  (let [selectors (persistent! @invalidated-selectors)
        map-selector->component @selector->component]
    (reset! invalidated-selectors (transient #{}))
    (set! queued? false)
    (doseq [s selectors
            component (map-selector->component s)]
      ;; Could be multimethod/protocol instead (e.g.: -receive-subscription-update)?
      (assert (not (undefined? (.-isMounted component))))
      (when (.isMounted component)                                  ;; react component
        (.forceUpdate component)))))

(defn flush-invalidated! []
   (batchedUpdates subscriber-notify!))

(def ReactManager (reify
                    proto/ExternalDependent
                    (-change-notify [this graph selector-changed]
                      (when-not queued?
                        (set! queued? true)
                        (goog.async.nextTick flush-invalidated!))
                      (swap! invalidated-selectors conj! selector-changed))))

(defn -add-dep [this react-component selector]
  (swap! selector->component update selector (fnil conj #{}) react-component))
(defn -remove-dep [this react-component selector]
  (swap! selector->component update selector (fnil disj #{}) react-component)
  ;(proto/-remove-external-dependent node this)
  )

(deftype ReactHitcher [graph react-component ^:mutable requests]
  proto/IBatching
  (-request-invalidations [_ invalidations]
    (proto/-request-invalidations graph invalidations) )
  (take-invalidations! [_] (proto/take-invalidations! graph))
  proto/IDependencyGraph
  (peek-node [this data-selector]
    (proto/peek-node graph data-selector))
  (create-node! [this data-selector]
    (proto/create-node! graph data-selector))
  (subscribe-node [this data-selector]
    (set! requests (conj requests data-selector))
    (if react-component
      (let [n (binding [proto/*read-mode* true] (proto/get-or-create-node graph data-selector))]
        (proto/-add-external-dependent n ReactManager)
        (-add-dep ReactManager react-component data-selector)
        (graph/normalize-tx! graph)
        n)
      (do  (prn data-selector "read outside of react render")
        (proto/get-or-create-node graph data-selector)))))


(defn react-hitcher [graph react-component]
  (->ReactHitcher graph react-component #{}))
