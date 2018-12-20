(ns react-hitch.qui-tracker
  (:import (goog.async run nextTick))
  (:require cljsjs.react
            cljsjs.react.dom
            [hitch2.halt :as halt]
            [hitch2.protocols.graph-manager :as graph-proto]
            [hitch2.protocols.tx-manager :as tx-manager]
            [hitch2.tx-manager.halting :as halting]
            [react-hitch.curator.react-hook :as rh]
            [breeze.quiescent :as q]))

(defn forceUpdate [c]
  (when (some? (.-__graph c))
    (.forceUpdate c))
  nil)

(defn forceUpdate-all [^not-native it]
  (while (.hasNext it)
    (forceUpdate (.next it))))

(defn batched-forceUpdate
  "Takes a :rerender-components effect and updates all components using React's
  unstable_batchedUpdates."
  [{:keys [components] :as rerender-component-effect}]
  (let [it (iter components)]
    (js/ReactDOM.unstable_batchedUpdates forceUpdate-all it)))

(defmethod graph-proto/run-effect :rerender-components
  [gm effect]
  (batched-forceUpdate effect))

(def idlecall (if (exists? (.-requestIdleCallback js/window))
                (fn [cb]
                  (js/requestIdleCallback cb))
                (fn [cb]
                  (js/setTimeout cb 30))))

(def pending-commands #js [])

(defn run-commands [graph]
  (fn []
    (let [commands pending-commands]
      (set! pending-commands #js [])
      (graph-proto/-transact-commands! graph commands))))

(defn queue-command [graph command]
  (when (zero? (alength pending-commands))
    (nextTick (run-commands graph)))
  (.push pending-commands command))

(defmethod graph-proto/run-effect :schedule-gc
  [gm effect]
  (idlecall
    (fn []
      (graph-proto/-transact! gm rh/react-hooker
        [:gc]))))

(defn flush-deps-on-unmount {:jsdoc ["@this {*}"]} []
  (this-as c
    (let [graph (.-__graph c)]
      (queue-command graph [rh/react-hooker [:reset-component-parents c #{}]]))))

(defn hitchify-component! [c graph]
  (when-not (some? (.-__graph c))
    (set! (.-__graph c) graph)
    (if-some [old-unmount (.-componentWillUnmount c)]
      (let [new-on-unmount (fn []
                             (this-as c
                               (.call old-unmount c)
                               (.call flush-deps-on-unmount c)))]
        (set! (.-componentWillUnmount c) new-on-unmount))
      (set! (.-componentWillUnmount c) flush-deps-on-unmount)))
  nil)

(defn qui-hitch
  ([graph unresolved c render-fn value services]
   (hitchify-component! c graph)
   (let [gv              (graph-proto/-get-graph graph)
         rtx             (halting/halting-manager gv)
         result          (halt/maybe-halt
                           (render-fn value rtx services)
                           unresolved)
         focus-descriptors (tx-manager/finish-tx! rtx)]
     (queue-command graph [rh/react-hooker [:reset-component-parents c focus-descriptors]])
     result))
  ([graph unresolved c render-fn value meta services]
   (hitchify-component! c graph)
   (let [gv              (graph-proto/-get-graph graph)
         rtx             (halting/halting-manager gv)
         result          (halt/maybe-halt
                           (render-fn value rtx meta services)
                           unresolved)
         focus-descriptors (tx-manager/finish-tx! rtx)]
     (queue-command graph [rh/react-hooker [:reset-component-parents c focus-descriptors]])
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
