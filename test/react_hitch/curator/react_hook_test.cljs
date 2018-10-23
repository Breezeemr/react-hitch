(ns react-hitch.curator.react-hook-test
  (:require [clojure.test :refer [#_deftest is testing use-fixtures]]
            [react-hitch.curator.react-hook :as rh]
            [react-hitch.common-test :refer [Constant]]
            [hitch2.selector :as sel]
            [hitch2.protocols.selector :as sel-proto]
            [hitch2.protocols.graph-manager :as graph-proto]
            [hitch2.graph :as graph]
            [hitch2.curator.mutable-var :refer  [mutable-var]]
            [hitch2.graph-manager.atom :as atom-gm]
            [hitch2.selector-impl-registry :as reg
             :refer [registry-resolver]]
            [devcards.core :refer-macros [deftest]]))

(reg/def-registered-selector constant-spec' constant-spec constant-impl)

(defn Constant [v]
  (sel-proto/sel constant-spec' v))

(def results (atom []))

(defmethod graph-proto/run-effect :rerender-components
  [gm effect]
  (swap! results conj effect))

(def gctors
  [["Atom graph: " (fn [] (atom-gm/make-gm registry-resolver))]])

(defn reset-rc-parents [gm rc new-parents]
  (graph-proto/-transact! gm rh/react-hooker [:reset-component-parents rc new-parents]))

(doseq [[gname gctor] gctors]
  (deftest simple-get-ok
    (let [g (gctor)]

      (reset! results [])

      (reset-rc-parents g :react-component #{(Constant 0)})

      (is (= @results [{:type :rerender-components, :components #{:react-component}}]))
      (reset! results []))))

(doseq [[gname gctor] gctors]
  (deftest Var-changes-trigger-batched-event
    (testing "updating an observed value will batch rerender all components"
      (let [g          (gctor)
            mv-sel     (mutable-var :mv)
            components (set (range 1000))]
        (reset! results [])
        (doseq [c components] (reset-rc-parents g c #{mv-sel}))

        ;; no events from selectors with no values
        (is (= @results []))

        (graph/apply-commands g [[mv-sel [:set-value 2]]])

        (is (= components (-> @results first :components)))))))