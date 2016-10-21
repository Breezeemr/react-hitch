(ns hitch.core-test
  (:require-macros [devcards.core :as dc :refer [deftest]])
  (:require [cljs.test :refer [] :refer-macros [is run-tests async]]
            [hitch.reactjs]
            [hitch.protocol :as proto]
            [hitch.selectors.mutable-var :refer [mutable-var]]
            [hitch.graph :as graph]))

