(ns hitch.reactjs
  (:require cljsjs.react
            cljsjs.react.dom
            [hitch.protocol :as proto]
            [hitch.graph :as graph]
            [cljs.core.async :as async]
            [clojure.set]
            [cljs.core.async.impl.protocols :as impl]
            [hitch.oldprotocols :as oldproto])
  (:import (goog.async nextTick)))

(def ^not-native batchedUpdates
  "The React batchUpdates addon. Takes one function which should call
  forceUpdate (possibly multiple times) inside it. This function will be called
  later and all components updated will have their forceUpdates reordered and
  applied in a single React transaction."
  js/ReactDOM.unstable_batchedUpdates)



(def ^:dynamic react-read-mode nil)
(defonce invalidated-components (atom (transient #{})))
(def queued? (transient false))
(defonce selector->component (atom {}))

(defn subscriber-notify! []
  (let [components (persistent! @invalidated-components)]
    (reset! invalidated-components (transient #{}))
    (vreset! queued? false)
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
        (when-not @queued?
          (vreset! queued? true)
          (nextTick flush-invalidated!))
        (swap! invalidated-components conj! this)))))

(defrecord ComponentWrapper [react-component]
  oldproto/ExternalDependent
  (-change-notify [this]
    (when-not react-read-mode
      (when-not @queued?
        (vreset! queued? true)
        (nextTick flush-invalidated!))
      (swap! invalidated-components conj! react-component))))

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
    (let [new-deps (persistent! new-requests)
          removed-deps (persistent! not-requested)
          new-old (reduce disj!
                          (transient (into old-requests new-deps))
                          removed-deps)]
      (set! old-requests (persistent! new-old))
      (set! not-requested (transient old-requests))
      (set! new-requests (transient #{}))
      (set! all-requests (transient #{}))
      (oldproto/update-parents graph react-component new-deps removed-deps)
      removed-deps)))


(defn react-hitcher [graph react-component olddeps]
  (->ReactHitcher graph (->ComponentWrapper react-component) olddeps (transient olddeps) (transient #{}) (transient #{})))

(defn get-react-hitcher [graph react-component]
  (if-let [react-manager (.-react-manager react-component)]
    react-manager
    (let [react-manager (react-hitcher graph react-component #{})]
        (set! (.-react-manager react-component) react-manager)
        react-manager)))

(defn react-hitch
  ([graph react-component nf xfn]
   (try
     (let [reacttx (get-react-hitcher graph react-component)]
       (binding [graph/*tx-manager* reacttx]
         (let [v (xfn reacttx)]
           (oldproto/enqueue-dependency-changes reacttx)
           v)))
     (catch :default ex (if (identical? oldproto/berror ex )
                          nf
                          (throw ex)))))
  ([graph react-component nf xfn a]
   (try
     (let [reacttx (get-react-hitcher graph react-component)]
       (binding [graph/*tx-manager* reacttx]
         (let [v (xfn reacttx a)]
           (oldproto/enqueue-dependency-changes reacttx)
           v)))
     (catch :default ex (if (identical? oldproto/berror ex )
                          nf
                          (throw ex)))))
  ([graph react-component nf xfn a b]
   (try
     (let [reacttx (get-react-hitcher graph react-component)]
       (binding [graph/*tx-manager* reacttx]
         (let [v (xfn reacttx a b)]
           (oldproto/enqueue-dependency-changes reacttx)
           v)))
     (catch :default ex (if (identical? oldproto/berror ex )
                          nf
                          (throw ex)))))
  ([graph react-component nf xfn a b c]
   (try
     (let [reacttx (get-react-hitcher graph react-component)]
       (binding [graph/*tx-manager* reacttx]
         (let [v (xfn reacttx a)]
           (oldproto/enqueue-dependency-changes reacttx)
           v) ))
     (catch :default ex (if (identical? oldproto/berror ex )
                          nf
                          (throw ex)))))
  ([graph react-component nf xfn a b c d]
   (try
     (let [reacttx (get-react-hitcher graph react-component)]
       (binding [graph/*tx-manager* reacttx]
         (let [v (xfn reacttx a b c d)]
           (oldproto/enqueue-dependency-changes reacttx)
           v)))
     (catch :default ex (if (identical? oldproto/berror ex )
                          nf
                          (throw ex)))))
  ([graph react-component nf xfn a b c d e]
   (try
     (let [reacttx (get-react-hitcher graph react-component)]
       (binding [graph/*tx-manager* reacttx]
         (let [v (xfn reacttx a b c d e)]
           (oldproto/enqueue-dependency-changes reacttx)
           v)))
     (catch :default ex (if (identical? oldproto/berror ex )
                          nf
                          (throw ex)))))
  ([graph react-component nf xfn a b c d e f]
   (try
     (let [reacttx (get-react-hitcher graph react-component)]
       (binding [graph/*tx-manager* reacttx]
         (let [v (xfn reacttx a b c d e f)]
           (oldproto/enqueue-dependency-changes reacttx)
           v)))
     (catch :default ex (if (identical? oldproto/berror ex )
                          nf
                          (throw ex)))))
  ([graph react-component nf xfn a b c d e f g]
   (try
     (let [reacttx (get-react-hitcher graph react-component)]
       (binding [graph/*tx-manager* reacttx]
         (let [v (xfn reacttx a b c d e f g)]
           (oldproto/enqueue-dependency-changes reacttx)
           v)))
     (catch :default ex (if (identical? oldproto/berror ex )
                          nf
                          (throw ex))))))
