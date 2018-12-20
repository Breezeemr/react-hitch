(ns react-hitch.curator.react-hook-test
  (:require [clojure.test :refer [#_deftest is testing use-fixtures]]
            [react-hitch.curator.react-hook :as rh]
            [react-hitch.common-test :refer [Constant] :as test]
            [hitch2.protocols.graph-manager :as graph-proto]
            [hitch2.graph :as graph]
            [hitch2.curator.mutable-var :refer  [mutable-var]]
            [hitch2.graph-manager.atom :as atom-gm]
            [hitch2.descriptor-impl-registry :as reg
             :refer [registry-resolver]]
            [devcards.core :refer-macros [deftest]]))

(def results (atom []))
(def gc-schedules (atom []))

(defn fixture [f]
  (defmethod graph-proto/run-effect :rerender-components
    [gm effect]
    (swap! results conj effect))
  (defmethod graph-proto/run-effect :schedule-gc
    [gm effect]
    (swap! gc-schedules conj effect))
  (f)
  (remove-method graph-proto/run-effect :rerender-components)
  (remove-method graph-proto/run-effect :schedule-gc))

(use-fixtures :once fixture)

(def gctors
  [["Atom graph: " (fn [] (atom-gm/make-gm registry-resolver test/sync-scheduler))]])

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
            components (set (range 100))]
        (reset! results [])
        (doseq [c components] (reset-rc-parents g c #{mv-sel}))

        ;; no events from descriptors with no values
        (is (= @results []))

        (graph/apply-commands g [[mv-sel [:set-value 2]]])

        (is (= components (-> @results first :components)))))))

(doseq [[gname gctor] gctors]
  (deftest unmounting-schedules-gc
    (let [g         (gctor)
          services  {:graph g}
          sels      (into #{} (map Constant) (range 400))
          component :rc]

      (reset! gc-schedules [])
      (reset-rc-parents g component sels)

      (reset-rc-parents g component #{})
      (testing "A gc is scheduled when the component is unmounted"
       (is (= @gc-schedules [{:type :schedule-gc}])))

      (reset! gc-schedules [])
      (reset-rc-parents g component #{(Constant 0)})

      (testing "The sweep happens only once even if it does not sweep everything"
        (is (= @gc-schedules []))))))
