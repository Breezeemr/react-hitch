(ns react-hitch.scheduler
  (:import (goog.async run nextTick))
  (:require [hitch2.protocols.graph-manager :as graph-proto]
            [react-hitch.curator.react-hook :as rh]
            [react-hitch.descriptor-specs :refer [react-hooker]]))


(def idlecall (if (exists? (.-requestIdleCallback js/window))
                (fn [cb]
                  (js/requestIdleCallback cb))
                (fn [cb]
                  (js/setTimeout cb 5000))))

(defonce ^:private LOADING #js{})

(defn loaded? [x]
  (not (identical? LOADING x)))

(def scheduled? false)
(def pending-commands #js [])
(def hsubs (volatile! (transient {})))

(defrecord valbox [x])

(defn per-graph-change-hook-subs [_ g subs]
  (let [graph-value (graph-proto/-get-graph g)
        subs        (persistent! subs)]
    (reduce-kv
      (fn [_ [setdtorval dtor] dtorval]
        (prn subs)
        (when dtorval
          (let [val     (get graph-value dtor LOADING)
                dtorval (:x ^valbox dtorval)]
            (when (and (loaded? val) (not= dtorval val))
              (setdtorval val)))))
      nil
      subs)

    (let [commands pending-commands]
      (set! pending-commands #js [])
      (.push commands [react-hooker
                       [:hook-subs subs]])
      (graph-proto/-transact-commands! g commands))))

(defn run-commands [graph]
  (fn []
    (set! scheduled? false)
    (let [subs (persistent! @hsubs)]
      (vreset! hsubs (transient {}))
      (reduce-kv
        per-graph-change-hook-subs
        nil
        (if (empty? subs)
          {graph (transient {})}
          subs))
      (when (pos? (.length pending-commands))
        (let [commands pending-commands]
          (set! pending-commands #js [])
          (graph-proto/-transact-commands! graph commands))))))

(defn schedule [graph]
  (when-not scheduled?
    (set! scheduled? true)
    (nextTick (run-commands graph))))


(defn queue-qui-tracker-command [graph command]
  (schedule graph)
  (.push pending-commands command))



(defn add-subscribe [m g x dtorval]
  (let [gdata (get m g (transient {}))
        v     (get gdata x)]
    (case v
      false (assoc! m g (dissoc! gdata x))
      nil (do
            (schedule g)
            (assoc! m g (assoc! gdata x dtorval)))
      m)))

(defn remove-subscribe [m g x]
  (let [gdata (get m g (transient {}))
        v     (get gdata x)]
    (case v
      false m
      nil (do
            (schedule g)
            (assoc! m g (assoc! gdata x false)))
      (assoc! m g (dissoc! gdata x)))))
