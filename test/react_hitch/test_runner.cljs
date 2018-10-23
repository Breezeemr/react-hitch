(ns react-hitch.test-runner
  (:require [cljs.test]
            [cljs-test-display.core]
            [react-hitch.curator.react-hook-test]
            [react-hitch.qui-tracker-test])
  (:require-macros [cljs.test]))

(defn test-run []
  (cljs.test/run-tests
   (cljs-test-display.core/init! "app")
   'react-hitch.curator.react-hook-test
   'react-hitch.qui-tracker-test))

(test-run)
