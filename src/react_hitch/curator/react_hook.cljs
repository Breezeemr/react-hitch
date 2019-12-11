(ns react-hitch.curator.react-hook
  (:require [hitch2.def.curator :as curator]
            [hitch2.def.spec :as def-spec
             :refer [def-descriptor-spec]]
            [hitch2.descriptor-impl-registry :as reg]
            [hitch2.graph :as graph]
            [react-hitch.descriptor-specs
             :refer [react-hitcher-process-spec
                     react-hook-spec react-hitcher-process
                     ->hook-dtor ms-until-unload]]
            [clojure.set :as set]))

(defn set-conj [coll x ]
  (if-some [coll coll]
    (conj coll x)
    (conj #{} x)))

(defrecord timedunload [time dtor])

(def initial-node
  (assoc curator/initial-curator-state
         :state {:rc->sel     {}
                 :sel->rc     {}
                 :dirty-sels    #{}
                 :gcable-sels #queue []
                 :gc-scheduled? false}))

(defn remove-called-hooks [state descriptors]
  (reduce dissoc state descriptors))

(defn reset-component-parents [node rc new-parents]
  (let [state               (:state node)
        rc->sel             (:rc->sel state)
        sel->rc             (:sel->rc state)
        old-parents         (get rc->sel rc #{})
        del-parents         (set/difference old-parents new-parents)
        add-parents         (set/difference new-parents old-parents)
        rc->sel'            (if (seq new-parents)
                              (assoc rc->sel rc new-parents)
                              (dissoc rc->sel rc))
        shared-parent-delta (volatile! {})
        sel->rc'            (as-> sel->rc <>
                              (reduce
                                (fn [sel->rc sel]
                                  (when (nil? (get sel->rc sel))
                                    (vswap! shared-parent-delta assoc sel true))
                                  (update sel->rc sel set-conj  rc))
                                <> add-parents)
                              (reduce
                                (fn [sel->rc sel]
                                  (let [rcs        (get sel->rc sel #{})
                                        rcs'       (disj rcs rc)
                                        went-away? (and (pos? (count rcs)) (zero? (count rcs')))]
                                    (when went-away?
                                      (vswap! shared-parent-delta assoc sel false))
                                    (if went-away?
                                      (dissoc sel->rc sel)
                                      (assoc sel->rc sel rcs'))))
                                <> del-parents))
        state'              (assoc state :rc->sel rc->sel'
                                         :sel->rc sel->rc')
        t                    (js/Date.now)]
    (-> node
        (assoc :state state')
        (update :change-focus into (remove (comp false? val)) @shared-parent-delta)
        (update :gcable-sels into
          (keep (fn [x]
                  (when (false? (val x))
                    (->timedunload t (key x)))))
          @shared-parent-delta))))

(defn handle-hook-subs [node subs]
  (let [state               (:state node)
        sel->rc             (:sel->rc state)
        t                    (js/Date.now)]
    ;(update node :change-focus assoc dtor true)
    (loop [sel->rc sel->rc
           tounload []
           toload  []
           subs (seq subs)]
      (if-let [[[statefn dtor] v] (first subs)]
        (if v
          (if-some [rcs (-> sel->rc
                          (get  dtor))]
            (recur (assoc sel->rc dtor (conj rcs statefn)) tounload toload (rest subs))
            (recur (assoc sel->rc dtor #{statefn}) tounload (conj toload dtor) (rest subs)))
          (if-some [rcs (-> sel->rc
                          (get  dtor #{})
                            (disj statefn)
                            not-empty)]
            (recur (assoc sel->rc dtor rcs) tounload toload (rest subs))
            (recur (dissoc sel->rc dtor) (conj tounload dtor) toload (rest subs))))
        (-> node
            (assoc-in [:state :sel->rc] sel->rc)
            (update :change-focus into (map (fn [x] [x true])) toload)
            (update :gcable-sels into
              (map (fn [x]
                     (->timedunload t x)))
              tounload))))))

(defn prep-rerender [sel->rc]
  (fn [sel]
    (let [hooks-or-comps (sel->rc sel)]
      (eduction
        (map (fn [hook-or-comp]
               (if (fn? hook-or-comp)
                 (->hook-dtor hook-or-comp sel)
                 hook-or-comp)))
        hooks-or-comps))))

(defn do-gc [node]
  (let [{:keys [sel->rc
                gcable-sels]
         :as   state}
        (-> node :state)
        current-t   (js/Date.now)]
    (loop [change-focus  (transient (:change-focus node))
           gcable-sels  gcable-sels
           totake        10]
      (if-some [sel (peek gcable-sels)]
        (if (and (> (- current-t (:time sel)) ms-until-unload)
              (pos? totake))
          (let [dtor (:dtor sel)]
            (if (sel->rc dtor)
              (recur change-focus (pop gcable-sels) totake)
              (recur (assoc! change-focus dtor false) (pop gcable-sels) (dec totake))))
          (assoc node :change-focus (persistent! change-focus)
                      :gcable-sels gcable-sels))
        (assoc node :change-focus (persistent! change-focus)
                    :gcable-sels gcable-sels)))))

(def react-hook-impl
  {:hitch2.descriptor.impl/kind
   :hitch2.descriptor.kind/curator

   ::curator/init
   (fn [curator-descriptor] initial-node)

   ::curator/apply-command
   (fn [curator-descriptor graph-value node command]
     (case (nth command 0)
       :reset-component-parents
       (let [[_ rc new-parents] command]
         (reset-component-parents node rc new-parents))
       :hook-subs
       (let [[_ sub-changes] command]
         (handle-hook-subs node sub-changes))
       :gc
       (-> node
           do-gc
           (assoc :state (-> node :state (assoc :gc-scheduled? false))))))

   ::curator/observed-value-changes
   (fn [curator-descriptor graph-value node parent-descriptors]
     (update-in node [:state :dirty-sels] into parent-descriptors))

   ::curator/finalize
   (fn [_ graph-value node]
     (let [{:keys [dirty-sels
                   gc-scheduled?
                   gcable-sels]}
           (-> node :state)]
       (cond-> (assoc-in node [:state :dirty-sels] #{})
         (pos? (count dirty-sels))
         (update :outbox conj
           [react-hitcher-process
            {:type       :rerender-components
             :components (into []
                           (mapcat (prep-rerender (-> node :state :sel->rc)))
                           dirty-sels)}])
         (and (not gc-scheduled?)
           (not-empty gcable-sels))
         (->
           (update :outbox conj
             [react-hitcher-process
              {:type :schedule-gc
               :when (-> gcable-sels peek :time)}])
           (assoc-in [:state :gc-scheduled?] true)))))})

(reg/def-registered-descriptor Rreact-hook react-hook-spec react-hook-impl)
