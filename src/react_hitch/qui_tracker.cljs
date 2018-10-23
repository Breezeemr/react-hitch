(ns react-hitch.qui-tracker
  (:require cljsjs.react
            cljsjs.react.dom
            [hitch2.halt :as halt]
            [hitch2.protocols.graph-manager :as graph-proto]
            [hitch2.protocols.tx-manager :as tx-manager]
            [hitch2.tx-manager.halting :as halting]
            [react-hitch.curator.react-hook :as rh]
            [quiescent :as q])
  (:import (goog.async nextTick)))

(def ^not-native batchedUpdates
  "The React batchUpdates addon. Takes one function which should call
  forceUpdate (possibly multiple times) inside it. This function will be called
  later and all components updated will have their forceUpdates reordered and
  applied in a single React transaction."
  js/ReactDOM.unstable_batchedUpdates)

(defn flush-deps-on-unmount []
  (this-as c
    (let [graph (.-__graph c)]
      (graph-proto/-transact! graph rh/react-hooker
                              [:reset-component-parents c #{}]))))


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

(defn hitch-render-wrapper [nf]
  (fn [render]
    (fn
      ([value services]
       (let [{:keys [graph]} services]
         (qui-hitch graph nf q/*component* render value services)))
      ;; ribbon meta
      ([value c-meta services]
       (let [graph (or (:graph services) (:graph c-meta) (:graph (first c-meta)))]
         (qui-hitch graph nf q/*component* render value c-meta services))))))
