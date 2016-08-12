(defproject react-hitch "0.1.0-SNAPSHOT"
  :description "A Clojurescript library designed to manage and cache derived data."
  :url "https://github.com/Breezeemr/react-hitch"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm "https://github.com/Breezeemr/react-hitch"
  :repositories [["snapshots" {:url "s3p://breezepackages/snapshots" :creds :gpg}]
                 ["releases" {:url "s3p://breezepackages/releases" :creds :gpg}]]
  :dependencies [[org.clojure/clojure "1.8.0" :scope "provided"]
                 [org.clojure/clojurescript "1.9.93" :scope "provided"]
                 [cljsjs/react-dom "0.14.0-1"]
                 [com.breezeehr/hitch "0.1.3-SNAPSHOT"]]
  :profiles {:dev {
                   :dependencies [[com.cemerick/piggieback "0.2.1"]    ; needed by figwheel nrepl
                                  [devcards "0.2.1"]]
                   :plugins [                               ;[lein-cljsbuild "1.1.1"]
                             [lein-figwheel "0.5.4-7"]]
                   :figwheel { :http-server-root "public"
                              ;; :http-server-root "public" ;; default and assumes "resources"
                              ;; :server-port 3449 ;; default
                              :css-dirs ["resources/public/css"] ;; watch and update CSS

                              ;; Start an nREPL server into the running figwheel process
                              :nrepl-port 7888

                              ;; Server Ring Handler (optional)
                              ;; if you want to embed a ring handler into the figwheel http-kit
                              ;; server, this is for simple ring servers, if this
                              ;; doesn't work for you just run your own server :)
                              ;; :ring-handler hello_world.server/handler

                              ;; To be able to open files in your editor from the heads up display
                              ;; you will need to put a script on your path.
                              ;; that script will have to take a file path and a line number
                              ;; ie. in  ~/bin/myfile-opener
                              ;; #! /bin/sh
                              ;; emacsclient -n +$2 $1
                              ;;
                              ;; :open-file-command "myfile-opener"

                              ;; if you want to disable the REPL
                              ;; :repl false

                              ;; to configure a different figwheel logfile path
                              ;; :server-logfile "tmp/logs/figwheel-logfile.log"
                              }}}
  :source-paths ["src"]

  :clean-targets ^{:protect false} ["resources/public/js" "target"]

  :cljsbuild {
              :builds [{:id           "devcards"
                        :source-paths ["src" "test"]

                        :figwheel     {:devcards true}

                        :compiler     {:main          hitch.test-runner
                                       :asset-path    "js/devcardsout"
                                       :output-to     "resources/public/js/react_hitch_devcards.js"
                                       :output-dir    "resources/public/js/devcardsout"
                                       :optimizations :none
                                       :source-map true
                                       :source-map-timestamp false
                                       :cache-analysis true
                                       }}
                       {:id           "min"
                        :source-paths ["src"]
                        :compiler     {:output-to     "resources/public/js/react-hitch.js"
                                       :main          hitch.reactjs
                                       :optimizations :advanced
                                       :pretty-print  false}}]})
