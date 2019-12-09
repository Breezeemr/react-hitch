(ns react-hitch.graph
  (:require
    [crinkle.component :as c]
    [cljsjs.react :as react]
    [goog.object :as gobj]))

(def GraphContext
  "The application's graph manager"
  (js/React.createContext nil))

(def GraphContext-Provider (.-Provider GraphContext))

(def GraphContext-Consumer (.-Consumer GraphContext))

(defn with-graph
  "Return an element which will run body-fn with a single argument: the graph
  manager"
  [body-fn]
  (c/RE GraphContext-Provider nil
    (fn [x] (body-fn (gobj/get x "value")))))
