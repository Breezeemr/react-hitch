(ns react-hitch.card.wrapper
  (:require [devcards.core :as dc]
            [react-hitch.qui-tracker :as qui]
            [breeze.quiescent :as q :refer-macros [defcomponent]]
            [breeze.quiescent.dom :as d]
            [hitch2.curator.mutable-var :refer  [mutable-var]]
            [hitch2.graph :as h]
            [hitch2.graph-manager.atom :as g]
            [hitch2.selector-impl-registry :as reg
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
  state)
