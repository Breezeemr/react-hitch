(ns react-hitch.qui-tracker-test
  (:require [clojure.test :refer [is testing use-fixtures]]
            [react-hitch.qui-tracker :as qui]
            [react-hitch.curator.react-hook :as rh]
            [react-hitch.common-test :as test]
            [hitch2.protocols.graph-manager :as graph-proto]
            [hitch2.graph :as h]
            [hitch2.curator.mutable-var :refer  [mutable-var]]
            [hitch2.graph-manager.atom :as atom-gm]
            [hitch2.descriptor-impl-registry :as reg :refer [registry-resolver]]
            [devcards.core :refer-macros [deftest]]))

(def results (atom []))

(defn fixture [f]
  (defmethod graph-proto/run-effect :rerender-components
    [gm effect]
    (swap! results conj effect))
  (f)
  (remove-method graph-proto/run-effect :rerender-components))

(use-fixtures :once fixture)

(def gctors
  [["Atom graph: " (fn [] (atom-gm/make-gm registry-resolver test/sync-scheduler))]])

(defn render-function [value rtx services]
  value)

(doseq [[gname gctor] gctors]
  (deftest doesnt-blow-up
    (let [graph        (gctor)
          services {:graph graph}
          value    3]
      (reset! results [])
      (is (= value (qui/qui-hitch graph :nf :component render-function value services)))
      (is (= [] @results)))))

(defn render-with-deps [value rtx services]
  @(h/select-sel! rtx value))

(doseq [[gname gctor] gctors]
  (deftest satisfiable-parents
    (let [g        (gctor)
          services {:graph g}
          mv-sel    (mutable-var :mv)]
      (reset! results [])
      (is (= :nf (qui/qui-hitch g :nf :component render-with-deps mv-sel services)))
      (is (= [] @results))

      (h/apply-commands g [[mv-sel [:set-value 42]]])
      (reset! results [])

      (is (= 42 (qui/qui-hitch g :nf :component render-with-deps mv-sel services)))
      (is (= [] @results))

      (h/apply-commands g [[mv-sel [:set-value 7]]])
      (is (= [{:type :rerender-components, :components #{:component}}]
             @results)))))
