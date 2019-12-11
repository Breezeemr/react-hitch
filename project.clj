(defproject com.breezeehr/react-hitch "0.4.1"
  :description "A Clojurescript library designed to manage and cache derived data."
  :url "https://github.com/Breezeemr/react-hitch"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :scm "https://github.com/Breezeemr/react-hitch"
  :repositories [["snapshots" {:url "s3p://breezepackages/snapshots" :creds :gpg}]
                 ["releases" {:url "s3p://breezepackages/releases" :creds :gpg}]]
  :dependencies [[org.clojure/clojure "1.10.1" :scope "provided"]
                 ;[org.clojure/clojurescript "1.10.520" :scope "provided"]
                 ;[com.google.javascript/closure-compiler-unshaded "v20190325"]
                 ;[org.clojure/google-closure-library "0.0-20190213-2033d5d9"]
                 [com.breezeehr/hitch2 "0.3.5" :exclusions [org.clojure/clojurescript ]]
                 [crinkle "2.0.0" :exclusions [org.clojure/clojurescript ]]
                 [thheller/shadow-cljs "2.8.74"]
                 [com.breezeehr/quiescent "0.3.0"
                  :exclusions [cljsjs/react-dom cljsjs/react cljsjs/create-react-class]]]
  :profiles {:dev {
                   :dependencies [                          ;[cider/piggieback "0.3.10"]    ; needed by figwheel nrepl
                                  [devcards "0.2.5"]
                                  [com.bhauman/cljs-test-display "0.1.1"]
                                  [com.bhauman/figwheel-main "0.2.0"]]
                   :source-paths ["test" "devcards"]
                   :resource-paths ["target"]
                   :clean-targets ^{:protect false} ["target"]
                   :aliases      {"fig"       ["trampoline" "run" "-m" "figwheel.main"]
                                  "build-dev" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]}}}
  :source-paths ["src"])
