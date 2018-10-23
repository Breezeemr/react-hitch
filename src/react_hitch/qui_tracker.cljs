(ns react-hitch.qui-tracker
  (:require cljsjs.react
            cljsjs.react.dom
            [hitch2.halt :as halt]            
            [hitch2.protocols.graph-manager :as graph-proto]
            [hitch2.protocols.tx-manager :as tx-manager]
            [hitch2.tx-manager.halting :as halting]
            [react-hitch.curator.react-hook :as rh])
  (:import (goog.async nextTick)))

(def ^not-native batchedUpdates
  "The React batchUpdates addon. Takes one function which should call
  forceUpdate (possibly multiple times) inside it. This function will be called
  later and all components updated will have their forceUpdates reordered and
  applied in a single React transaction."
  js/ReactDOM.unstable_batchedUpdates)


(defn qui-hitch
  ([graph unresolved c render-fn value services]
   (let [gv              (graph-proto/-get-graph graph)
         rtx             (halting/halting-manager gv)
         result          (halt/maybe-halt
                          (render-fn value rtx services)
                          unresolved)
         focus-selectors (tx-manager/finish-tx! rtx)]
     (graph-proto/-transact! graph rh/react-hooker
                   [:reset-component-parents c focus-selectors])
     result))
  ([graph unresolved c render-fn value meta services]
   (let [gv              (graph-proto/-get-graph graph)
         rtx             (halting/halting-manager gv)
         result          (halt/maybe-halt
                          (render-fn value rtx meta services)
                          unresolved)
         focus-selectors (tx-manager/finish-tx! rtx)]
     (graph-proto/-transact! graph rh/react-hooker
                   [:reset-component-parents c focus-selectors])
     result)))
