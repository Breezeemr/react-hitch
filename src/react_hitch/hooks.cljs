(ns react-hitch.hooks
  (:require
    ["react" :as react]
    [hitch2.graph :as h]
    [hitch2.protocols.graph-manager :as graph-proto]
    [react-hitch.scheduler :as sched]
    [react-hitch.graph :refer [GraphContext]]
    [react-hitch.descriptor-specs :refer [react-hooker]]
    [crinkle.component :as c]
    [goog.async :as async]))

(def LOADING sched/LOADING)

(defn loaded? [x]
  (not (identical? sched/LOADING x)))

(defn- get-dtor [gm dtor nf]
  ;; TODO: -get-graph marked deprecated, unsure of replacement
  (get (graph-proto/-get-graph gm) dtor nf))
(defn useEquiv
  "Takes a value and a comparison function. Returns an equivalent (but not
  necessarily identical) value. The comparison function must be hook-static.
  The comparison function must take an old and new object and return true if
  they are equal and false if they are not.

  The identity of the returned object will only change on subsequent renders
  if the comparison function returns false; otherwise it will return the
  equivalent object from a previous render so that hooks that look for
  deps array changes will not detect a change.

  This function exists because useMemo, useEffect & company do not accept a
  custom comparator, so immutable equal-but-not-identical values retrigger
  these hooks unnecessarily. Use this hook on values you intend to include
  in a deps array.

  Example use:

     (let [v (useEquiv v =)
           m (react/useMemo #(some-expensive-fn v) #js[v])]
       ,,,)

  Note the comparison function runs during the render phase, so keep it fast.

  See also `useEquivDeps`, `use=`, `use=deps`."
  [value equal?]
  (let [vref (react/useRef value)
        oldv (.-current vref)]
    (if ^boolean (equal? value oldv)
      ;; unsure if better to have an always-setting fn and include value in the
      ;; deps; or to do this.
      oldv
      (do (set! (.-current vref) value)
          value))))

(defn use=
  "Like `useEquiv`, but with a hardcoded cljs = comparison function"
  [value]
  (let [vref (react/useRef value)]
    (if (= value (.-current vref))
      (.-current vref)
      (do (set! (.-current vref) value)
          value))))

(defn useSelectedRaw
  [g dtor not-found]
  (let [s          (react/useState (get-dtor g dtor not-found))
        dtorval    (aget s 0)
        setdtorval (aget s 1)]
    (react/useEffect
      (fn []
        (vswap! sched/hsubs sched/add-subscribe g [setdtorval dtor] (sched/->valbox dtorval))
        #(vswap! sched/hsubs sched/remove-subscribe g [setdtorval dtor]))
      #js[dtor])
    dtorval))

(defn useSelected
  "This hook requires that GraphContext be set."
  ([dtor]
   (useSelected dtor LOADING))
  ([dtor not-found]
   (let [dtor       (use= dtor)
         g          (react/useContext GraphContext)]
     (useSelectedRaw g dtor not-found))))
