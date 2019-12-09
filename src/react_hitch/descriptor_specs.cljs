(ns react-hitch.descriptor-specs
  (:require [hitch2.def.spec
             :refer [def-descriptor-spec]]
            [hitch2.graph :as graph]))

(def-descriptor-spec react-hook-spec
  :curator
  :canonical-form :vector)

(def-descriptor-spec react-hitcher-process-spec
  :process)

(def react-hooker (graph/positional-dtor react-hook-spec))

(def react-hitcher-process (graph/->dtor react-hitcher-process-spec nil) )
