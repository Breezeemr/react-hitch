(defproject com.breezeehr/react-hitch "0.2.0-SNAPSHOT"
  :description "A Clojurescript library designed to manage and cache derived data."
  :url "https://github.com/Breezeemr/react-hitch"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :scm "https://github.com/Breezeemr/react-hitch"
  :repositories [["snapshots" {:url "s3p://breezepackages/snapshots" :creds :gpg}]
                 ["releases" {:url "s3p://breezepackages/releases" :creds :gpg}]]
  :dependencies [[org.clojure/clojure "1.9.0" :scope "provided"]
                 [org.clojure/clojurescript "1.10.339" :scope "provided"]
                 [cljsjs/react-dom "0.14.0-1"]
                 [com.breezeehr/hitch2 "0.1.0-SNAPSHOT"]
                 [com.breezeehr/quiescent "0.2.0-SNAPSHOT"
                  :exclusions [cljsjs/react-dom cljsjs/react]]]
  :profiles {:dev {
                   :dependencies [[cider/piggieback "0.3.9"]    ; needed by figwheel nrepl
                                  [devcards "0.2.5"]
                                  [com.bhauman/cljs-test-display "0.1.1"]
                                  [com.bhauman/figwheel-main "0.2.0-SNAPSHOT"]]
                   :source-paths ["test" "devcards"]
                   :resource-paths ["target"]
                   :clean-targets ^{:protect false} ["target"]
                   :aliases      {"fig"       ["trampoline" "run" "-m" "figwheel.main"]
                                  "build-dev" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]}}}
  :source-paths ["src"])
