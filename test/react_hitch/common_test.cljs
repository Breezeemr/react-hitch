(ns react-hitch.common-test
  (:require [hitch2.def.spec :as def-spec
             :refer [def-descriptor-spec]]
            [hitch2.graph :as graph]
            [hitch2.descriptor-impl-registry :as reg]
            [hitch2.protocols.graph-manager :as g]))

(defn return-constant [ v]
  (fn [gv-tracker]
    v))

(def-descriptor-spec constant-spec
  :not-curator
  :canonical-form :vector
  :positional-params [:v])

(def constant-impl
  {:hitch2.descriptor.impl/kind                  :hitch2.descriptor.kind/halting
   :hitch2.descriptor.impl/halting               return-constant
   :hitch2.descriptor.impl/halting-slot-descriptor (fn [_dt _sel v] v)})

(reg/def-registered-descriptor constant-spec' constant-spec constant-impl)

(defn Constant [v]
  (graph/positional-dtor constant-spec' v))

(def sync-scheduler
  (reify g/IScheduler
    (-run-sync [_ gm effects]
      (run! (fn [effect] (g/run-effect gm effect)) effects))
    (-run-async [_ gm effects]
      (run! (fn [effect] (g/run-effect gm effect)) effects))))
