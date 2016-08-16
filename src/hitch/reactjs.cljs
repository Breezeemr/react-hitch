(ns hitch.reactjs
  (:require cljsjs.react
            cljsjs.react.dom
            [hitch.protocols :as proto]
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




(defonce invalidated-react-elements (atom #{}))
(def queued? false)

(defn subscriber-notify! []
  (let [subscribers @invalidated-react-elements]
    (reset! invalidated-react-elements #{})
    (set! queued? false)
    (doseq [s subscribers]
      ;; Could be multimethod/protocol instead (e.g.: -receive-subscription-update)?
      (when-not (undefined? (.-isMounted s))
        (when (.isMounted s)                                ;; react component
          (.forceUpdate s))))))
(defn flush-invalidated! []
   (batchedUpdates subscriber-notify!))

(defrecord ReactInvalidateWrapper [react-component]
  proto/ISubscriber
  (-recalculate! [node graph]
    (when-not queued?
      (set! queued? true)

      (goog.async.nextTick flush-invalidated!)
      )
    (swap! invalidated-react-elements conj react-component)
    :value-unchanged))

(deftype ReactHitcher [graph react-component ^:mutable requests]
  proto/IBatching
  (-request-effects [_ effects]
    (proto/-request-effects graph effects))
  (-request-invalidations [_ invalidations]
    (proto/-request-invalidations graph invalidations) )
  (take-effects! [_] (proto/take-effects! graph))
  (take-invalidations! [_] (proto/take-invalidations! graph))
  proto/IDependencyGraph
  (peek-node [this data-selector]
    (proto/peek-node graph data-selector))
  (create-node! [this data-selector]
    (proto/create-node! graph data-selector))
  (subscribe-node [this data-selector]
    (set! requests (conj requests data-selector))
    (let [n (proto/get-or-create-node graph data-selector)
          changes (proto/node-depend! n (->ReactInvalidateWrapper react-component ))]
      (when-let [[new-effects new-invalidates] changes]
        (proto/-request-effects graph new-effects)
        (proto/-request-invalidations graph new-invalidates))
      n)))


(defn react-hitcher [graph react-component]
  (->ReactHitcher graph react-component #{}))

(defn new-selectors [oldtx newtx]
  (if oldtx
    (clojure.set/difference (.-requests newtx) (.-requests oldtx))
    newtx))

(defn retired-selectors [oldtx newtx]
  (if oldtx
    (clojure.set/difference (.-requests oldtx) (.-requests newtx))
    #{}))
