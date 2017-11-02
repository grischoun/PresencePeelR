(ns presencepeelr.core
  (:require [konserve.memory :refer [new-mem-store]]
            [replikativ.peer :refer [client-peer]]
            [replikativ.stage :refer [create-stage! connect! subscribe-crdts!]]
            [hasch.core :refer [uuid]]
            [replikativ.crdt.ormap.realize :refer [stream-into-identity!]]
            [replikativ.crdt.ormap.stage :as s]
            [cljs.core.async :refer [>! chan timeout]]
            [superv.async :refer [S] :as sasync]
            [om.next :as om :refer-macros [defui] :include-macros true]
            [om.dom :as dom :include-macros true]
            [taoensso.timbre :as timbre]
            [sablono.core :as html :refer-macros [html]])
  (:require-macros [superv.async :refer [go-try <? go-loop-try]]
                   [cljs.core.async.macros :refer [go-loop]]))

(enable-console-print!)

(def user "mail:admin@presencepeelr.ch")

;; New id to be used when data structure is ok
;; (def ormap-id #uuid "0dd1aa20-b358-4f57-aa7b-4fef7721d079")

(def ormap-id #uuid "07f6aae2-2b46-4e44-bfd8-058d13977a8a")

(def uri "ws://127.0.0.1:31778")

(defonce val-atom (atom {:captures #{}}))

(def stream-eval-fns
  {'add    (fn [S a new]
             (swap! a update-in [:captures] conj new)
             a)
   'remove (fn [S a new]
             (swap! a update-in [:captures] (fn [old] (set (remove #{new} old))))
             a)})

(defn setup-replikativ []
  (go-try
   S
   (let [store  (<? S (new-mem-store))
         peer   (<? S (client-peer S store))
         stage  (<? S (create-stage! user peer))
         stream (stream-into-identity! stage [user ormap-id] stream-eval-fns val-atom)]
     (<? S (s/create-ormap! stage :description "captures" :id ormap-id))
     (connect! stage uri)
     {:store  store
      :stage  stage
      :stream stream
      :peer   peer})))

(declare replikativ-state)

(defn create-capture [event date team presences]
  {:event     event
   :date      date
   :team      team
   :presences presences})


(defn capture-key [capture]
  [(:date capture) (:event capture) (:team capture)])


(defn add-capture! [state capture]
  (s/assoc! (:stage state)
            [user ormap-id]
            (capture-key capture)
            [['add capture]]))


(defn remove-capture! [state capture]
  (s/dissoc! (:stage state)
            [user ormap-id]
            (capture-key capture)
            [['remove capture]]))



(defn input-widget [component placeholder local-key]
  [:input {:value       (get (om/get-state component) local-key)
           :placeholder placeholder
           :on-change   (fn [e]
                          (om/update-state! ;; Equivalent to a React setState.
                           ;; Here, assoc local-key to the comp.'s val
                           component
                           assoc
                           local-key
                           (.. e -target -value)))}])

(defui App
  Object
  (componentWillMount
   [this]
   (om/set-state! this {:input-event     ""
                        :input-date      ""
                        :input-presences ""}))
  (render [this]
          (let [{:keys [input-event input-date input-presences]} (om/get-state this)
                {:keys [captures]}                               (om/props this)]
            (html
             [:div
              [:div.widget
               [:h1 "PrezPeelR"]
               (input-widget this "Event" :input-event)
               (input-widget this "Start Date-Time" :input-date)
               [:button
                {:on-click (fn [_]
                             (let [new-capture (create-capture input-event
                                                               input-date
                                                               "Genève Lully"
                                                               {})]
                               (do
                                 (add-capture! replikativ-state new-capture)
                                 (om/update-state! this assoc :input-event "")
                                 (om/update-state! this assoc :input-date "")
                                 (om/update-state! this assoc :input-presences ""))))}
                "Add"]]
        [:div.widget
         [:table
          [:tr
           [:th "Event"]
           [:th "Date"]
           [:th ""]]
          (mapv
           (fn [{:keys [event date team presences]}]
             [[:tr
                [:td event]
                [:td date]
                [:td [:button
                      {:on-click (fn [_]
                                   (let [capture (create-capture event
                                                                 date
                                                                 team
                                                                 presences)]
                                     (do
                                       (remove-capture! replikativ-state capture)
                                       (om/update-state! this assoc :input-event "")
                                       (om/update-state! this assoc :input-date "")
                                       (om/update-state! this assoc :input-presences ""))))}
                      "Remove"]]]
              (input-widget this "Player" :input-player)
              [:button "Coming"]
              [:button "Not Sure"]
              [:button "Not Coming"]])
           captures)]]]))))


(defn main [& args]
  (go-try S (def replikativ-state (<? S (setup-replikativ))))
  (.error js/console "Presencepeelr connected ...."))

(def reconciler
  (om/reconciler {:state val-atom}))

(om/add-root! reconciler App (.getElementById js/document "app"))


(comment

  (remove #{1} #{1 2 3})

  (-> val-atom
      deref
      :captures)

  (.log js/console (clj->js stream-eval-fns))

  )
