(ns react-hitch.scheduler
  (:import (goog.async run nextTick))
  (:require cljsjs.react
            cljsjs.react.dom
            [hitch2.protocols.graph-manager :as graph-proto]
            [react-hitch.curator.react-hook :as rh]
            [react-hitch.descriptor-specs :refer [react-hooker]]))

(defonce ^:private LOADING
         (reify
           Object
           (toString [_] "#<LOADING>")
           IPrintWithWriter
           (-pr-writer [_ writer opts]
             (-write writer "#<LOADING>"))))

(defn loaded? [x]
  (not (identical? LOADING x)))

(def idlecall (if (exists? (.-requestIdleCallback js/window))
                (fn [cb]
                  (js/requestIdleCallback cb))
                (fn [cb]
                  (js/setTimeout cb 5000))))

(defrecord GraphDepChanges [quichanges hookchanges])

(defn graph-dep-changes []
  (->GraphDepChanges (transient (hash-map)) (transient (hash-map))))

(def new-pending (js/Map.))

(defrecord valbox [x])

(defn update-resolved-hooks [g subs quichanges]
  (let [graph-value (graph-proto/-get-graph g)]
    (reduce-kv
      (fn [_ c descriptors]
        (when (and (not-empty descriptors)
                   (every?
                     (fn [dtor]
                       (loaded? (get graph-value dtor LOADING)))
                     descriptors))
          (when (some? (.-__graph c))
            (.forceUpdate c))))
      nil
      quichanges)
    (reduce-kv
      (fn [_ [setdtorval dtor] dtorval]
        (when dtorval
          (let [val     (get graph-value dtor LOADING)
                dtorval (:x ^valbox dtorval)]
            (when (not= dtorval val)
              (setdtorval val)))))
      nil
      subs))
  )

(defn per-graph-change-hook-subs [{:keys [quichanges hookchanges]} g whole]
  (let [quichanges (persistent! quichanges)
        subs (persistent! hookchanges)]
    (let [commands (cond-> []
                       true             (into (map (fn [me]
                                                     [react-hooker [:reset-component-parents (key me) (val me)]]))
                                              quichanges)
                       (not-empty subs) (conj [react-hooker
                                               [:hook-subs subs]]))]
      ;(prn commands)
      (when (not-empty commands)
        (graph-proto/-transact-commands! g commands)))
    (js/ReactDOM.unstable_batchedUpdates (fn [] (update-resolved-hooks g subs quichanges)))))

(defn run-commands [graph]
  (fn []
    (let [pending new-pending]
      (set! new-pending (js/Map.))
      (.forEach pending per-graph-change-hook-subs))))
;:reset-component-parents
(defn schedule [graph]
  (nextTick (run-commands graph)))


(defn queue-qui-tracker-command [g c descriptors]
  (if-some [gdata (.get new-pending g)]
    (let [gdata (:quichanges gdata)]
      (assoc! gdata c descriptors))
    (do
      (.set new-pending g (graph-dep-changes))
      (schedule g)
      (recur g c descriptors))))



(defn add-subscribe [g x dtorval]
  (if-some [gdata (.get new-pending g)]
    (let [gdata (:hookchanges gdata)
          v     (get gdata x)]
      (case v
        false (dissoc! gdata x)
        nil (assoc! gdata x dtorval)
        ))
    (do
      (.set new-pending g (graph-dep-changes))
      (schedule g)
      (recur g x dtorval)))
  )

(defn remove-subscribe [g x]
  (if-some [gdata (.get new-pending g)]
    (let [gdata (:hookchanges gdata)
          v     (get gdata x)]
      (case v
        false nil
        nil (assoc! gdata x false)
        (dissoc! gdata x)))
    (do
      (.set new-pending g (graph-dep-changes))
      (schedule g)
      (recur g x))))
