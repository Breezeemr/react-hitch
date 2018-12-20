(ns react-hitch.curator.react-hook
  (:require [hitch2.def.curator :as curator]
            [hitch2.def.spec :as def-spec
             :refer [def-descriptor-spec]]
            [hitch2.descriptor-impl-registry :as reg]
            [hitch2.graph :as graph]
            [clojure.set :as set]))

(def initial-node
  (assoc curator/initial-curator-state
         :state {:rc->sel     {}
                 :sel->rc     {}
                 :dirty-rc    #{}
                 :gcable-sels #{}
                 :gc-scheduled? false}))

(defn remove-called-hooks [state descriptors]
  (reduce dissoc state descriptors))

(def-descriptor-spec react-hook-spec
  :curator
  :canonical-form :vector)

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
                                  (update sel->rc sel (fnil conj #{}) rc))
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
                                         :sel->rc sel->rc')]
    (-> node
        (assoc :state state')
        (update-in [:state :gcable-sels] into
                   (comp (filter (comp false? val))
                         (map key))
                   @shared-parent-delta)
        (update :change-focus into (remove (comp false? val)) @shared-parent-delta))))

(def react-hook-impl
  {:hitch2.descriptor.impl/kind :hitch2.descriptor.kind/curator

   ::curator/init
   (fn [curator-descriptor] initial-node)

   ::curator/apply-command
   (fn [curator-descriptor graph-value node command]
     (case (nth command 0)
       :reset-component-parents
       (let [[_ rc new-parents] command]
         (reset-component-parents node rc new-parents))
       :gc
       (let [{:keys [sel->rc
                     gc-scheduled?
                     gcable-sels]
              :as state}
             (-> node :state)]
         (-> node
           (update  :change-focus
             into
             (comp
               (take 100)
               (remove sel->rc)
               (map (fn [sel]
                      [sel false])))
             gcable-sels)
             (assoc  :state
                     (assoc state
                       :gcable-sels (into #{} (drop 100) gcable-sels)
                       gc-scheduled? false))))))

   ::curator/observed-value-changes
   (fn [curator-descriptor graph-value node parent-descriptors]
     (let [sel->rc   (-> node :state :sel->rc)
           dirty-rc  (-> node :state :dirty-rc)
           dirty-rc' (transduce
                       (map sel->rc)
                       into dirty-rc parent-descriptors)]
       (assoc-in node [:state :dirty-rc] dirty-rc')))

   ::curator/finalize
   (fn [_ graph-value node]
     (let [{:keys [dirty-rc
                   gc-scheduled?
                   gcable-sels]}
           (-> node :state)]
       (cond-> (assoc-in node [:state :dirty-rc] #{})
         (pos? (count dirty-rc))
         (update :async-effects conj
           {:type       :rerender-components
            :components dirty-rc})
         (and (not gc-scheduled?)
              (not-empty gcable-sels))
         (->
          (update :async-effects conj
                  {:type :schedule-gc})
          (assoc-in [:state :gc-scheduled?] true)))))})

(reg/def-registered-descriptor Rreact-hook react-hook-spec react-hook-impl)

(def react-hooker (graph/positional-dtor Rreact-hook))
