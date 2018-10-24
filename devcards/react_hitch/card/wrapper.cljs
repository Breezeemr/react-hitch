(ns react-hitch.card.wrapper
  (:require [devcards.core :as dc]
            [react-hitch.qui-tracker :as qui])
  (:require-macros [devcards.core :refer [defcard]]))

(def stuff 3)

(defcard devcard-options-example-name
  "Devcard options documentation."
  (reify dc/IDevcardOptions
    (-devcard-options [_ opts]
      (assoc opts :main-obj opts))) ;; <-- alter :main-obj to be the passed in opts
  {:devcards-options-init-state true})


(defcard hi
  "hi"
  {}
  {}
  {})

(defonce state (atom {:initial "state"}))




(defcard app-card
  "Example card"
  state)
