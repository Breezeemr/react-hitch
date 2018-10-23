(ns react-hitch.reactjs
  (:require cljsjs.react
            cljsjs.react.dom
            [hitch2.protocols.curator :as machine-proto]
            [hitch2.protocols.graph-manager :as graph-proto]
            [hitch2.sentinels :refer [NOT-FOUND-SENTINEL]]
            [hitch2.protocols.selector :as sel-proto
             :refer [def-selector-spec]]
            [hitch2.selector-impl-registry :as reg])
  (:import (goog.async nextTick)))

(def ^not-native batchedUpdates
  "The React batchUpdates addon. Takes one function which should call
  forceUpdate (possibly multiple times) inside it. This function will be called
  later and all components updated will have their forceUpdates reordered and
  applied in a single React transaction."
  js/ReactDOM.unstable_batchedUpdates)
