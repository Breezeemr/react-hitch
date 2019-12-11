(ns react-hitch.card.wrapper
  (:require [devcards.core :as dc]
            [react-hitch.qui-tracker :as qui]
            [breeze.quiescent :as q :refer-macros [defcomponent]]
            [breeze.quiescent.dom :as d]
            [hitch2.curator.mutable-var :refer  [mutable-var]]
            ["react" :as react]
            [react-hitch.graph :refer [GraphContext-Provider GraphContext]]
            [react-hitch.hooks :refer [useSelected]]
            [hitch2.graph :as h]
            [hitch2.graph-manager.atom :as g]
            [crinkle.component :as c :refer [CE RE]]
            [hitch2.descriptor-impl-registry :as reg
             :refer [registry-resolver]])
  (:require-macros [devcards.core :refer [defcard]]))

(def mv-sel (mutable-var :devcard))

(defcomponent
  ^{:wrap-render (qui/hitch-render-wrapper (d/h3 {:className "alert"} "Loading"))}
  Hitch-Aware
  [mv-sel rtx services]
  (let [val @(h/select-sel! rtx mv-sel)]
    (d/div nil "loaded val: " val)))

(def value (atom 0))

(defcomponent Loader
  [mv-sel {:keys [graph] :as services}]
  (d/div nil
         (d/button {:onClick (fn [e]
                               (swap! value inc)
                               (h/apply-commands graph [[mv-sel [:set-value @value]]]))}
                   "inc")
         (d/button {:onClick (fn [e]
                               (reset! value 0)
                               (h/apply-commands graph [[mv-sel [:clear]]]))}
                   "clear")
         (Hitch-Aware mv-sel services)))


(defn
  Hitch-Aware-hook
  [{:keys [mv-sel] :as props}]
  (let [val (useSelected mv-sel)]
    (d/div nil "loaded val: " (pr-str val))))


(defn Loader-hook
  [{:keys [mv-sel] :as props}]
  (let [graph (react/useContext GraphContext)]
    (d/div nil
      (d/button {:onClick (fn [e]
                            (swap! value inc)
                            (h/apply-commands graph [[mv-sel [:set-value @value]]]))}
        "inc")
      (d/button {:onClick (fn [e]
                            (reset! value 0)
                            (h/apply-commands graph [[mv-sel [:clear]]]))}
        "clear")
      (CE Hitch-Aware-hook props))))

(defn get-services []
  {:graph (g/make-gm registry-resolver)})

(defcard thing
  "stuff"
  (fn [_ _]
    (let [services (get-services)]
      (Loader mv-sel services))))

(defonce state (atom {:initial "state"}))

(defcard app-card
  "Example card"
  (fn [_ _]
    (let [services (get-services)]
      (RE GraphContext-Provider #js {:value (:graph services)}
        (CE Loader-hook {:mv-sel mv-sel})))))
