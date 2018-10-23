(ns react-hitch.common-test
  (:require [hitch2.protocols.selector :as sel-proto]
            [hitch2.selector-impl-registry :as reg]))

(defn return-constant [gv-tracker v]
  v)
(sel-proto/def-selector-spec constant-spec
  :not-machine
  :hitch.selector.spec/canonical-form :hitch.selector.spec.canonical-form/positional
  :hitch.selector.spec/positional-params [:v])
(def constant-impl
  {:hitch.selector.impl/kind :hitch.selector.kind/halting
   :hitch.selector.impl/halting return-constant
   :hitch.selector.impl/halting-slot-selector (fn [_dt _sel v] v)})

(reg/def-registered-selector constant-spec' constant-spec constant-impl)

(defn Constant [v]
  (sel-proto/sel constant-spec' v))