(ns hitch.core-test
  (:require-macros [hitch.eager :refer [go]]
                   [devcards.core :as dc :refer [deftest]])
  (:require [cljs.test :refer [] :refer-macros [is run-tests async]]
            [hitch.reactjs]
            [hitch.protocols :as proto]
            [hitch.selectors.mutable-var :refer [mutable-var]]
            [hitch.graph :as graph]
            [cljs.core.async :as async]
            [hitch.graphs.mutable :as mgraph]))

(def tempob (js-obj))
(deftest firstt
         (is (= (into #{}
                      [(hitch.reactjs/->ReactInvalidateWrapper tempob) (hitch.reactjs/->ReactInvalidateWrapper tempob)(hitch.reactjs/->ReactInvalidateWrapper tempob)])
                #{(hitch.reactjs/->ReactInvalidateWrapper tempob)} )))
