(ns job-streamer.console.components.calendars
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]
            [cljs.core.async :refer [put! <! chan pub sub unsub-all]]
            [clojure.browser.net :as net]
            [goog.events :as events]
            [goog.ui.Component]
            [job-streamer.console.api :as api]
            [job-streamer.console.format :as fmt])
  (:use [cljs.reader :only [read-string]])
  (:import [goog.net.EventType]
           [goog.events EventType]))

(defcomponent calendar-detail-view [cal-name owner]
  (will-mount [_]
    (api/request (str "/calendar/" cal-name) 
                 {:handler (fn [response]
                             (om/set-state! owner :calendar response))}))
  (render-state [_ {:keys [calendar]}]
    (html
     (if calendar
       [:div]
       [:img {:src "/img/loader.gif"}]))))

(defcomponent calendar-edit-view [app owner]
  (init-state [_]
    {:weekly-holiday [true false false false false false true]
     :calendar nil})
  (render-state [_ {:keys [weekly-holiday calendar]}]
    (html
     [:form.ui.form
      [:h4.ui.dividing.header "New calendar"]
      [:div.fields
       [:div.field
        [:label "Calendar name"]
        [:input {:type "text" :id "cal-name"}]]
       [:div.field
        [:div.ui.buttons
         (map-indexed
          (fn [idx weekday]
            [:button.ui.toggle.button
             {:type "button"
              :class (when (nth weekly-holiday idx) "active")
              :on-click (fn [_]
                          (om/update-state! owner [:weekly-holiday idx] #(not %))
                          (set! (.-blackout calendar)
                                (fn [d]
                                  (nth (->> (om/get-state owner :weekly-holiday)
                                            (map #(if % 1 0)))
                                       (.. js/Kalendae (moment d) day))))
                          (.draw (om/get-state owner :calendar)))}
             weekday])
          ["Sun" "Mon" "Tue" "Wed" "Thu" "Fri" "Stu"])]
        [:div#holiday-selector]]]
      [:button.ui.button.submit
       {:type "button"
        :on-click (fn [_]
                    (println (pr-str
                              {:holidays (js->clj (.getSelectedAsDates (om/get-state owner :calendar))) 
                               :weekly-holiday (om/get-state owner :weekly-holiday)})))}
       "Save"]]))
  (did-mount [_]
    (om/set-state! owner :calendar
                   (js/Kalendae. (clj->js {:attachTo (.getElementById js/document "holiday-selector")
                                           :months 4
                                           :mode "multiple"})))
    ))

(defcomponent calendar-list-view [calendars owner]
  (render [_]
    (html
     [:div.ui.grid
      [:div.ui.two.column.row
        [:div.column
         [:button.ui.button {:type "button"
                             :on-click #(set! (.-href js/location) "#/calendars/new")} "New"]]]
      [:div.row
       (when (not-empty calendars)
        [:table.ui.celled.striped.table
         [:thead
          [:tr
           [:th "Name"]
           [:th "Holidays"]]]
         [:tbody
          (for [cal calendars]
            [:tr
             [:td
              [:a {:href (str "#/calendar/" (:calendar/name cal))}
               (:calendar/name cal)]]
             [:td ]])]])]])))

(defcomponent calendars-view [app owner]
  (will-mount [_]
    (api/request "/calendars"
                 {:handler (fn [response]
                             (om/update! app :calendars response))}))
  (render [_]
    (html
     [:div.ui.grid
      [:div.ui.row
       [:div.ui.column
        [:h2.ui.purple.header
         [:i.laptop.icon]
         [:div.content
          "Calendar"
          [:div.sub.header "Calendars"]]]]]
      [:div.ui.row
       [:div.ui.column
        (let [mode (second (:mode app))]
          (case mode
            :new (om/build calendar-edit-view (select-keys app [:calendars :mode]))
            :detail (om/build calendar-detail-view (select-keys app [:calendars :mode]))
            ;; default
            (cond
              (nil? (:calendars app)) [:img {:src "/img/loader.gif"}]
              :default (om/build calendar-list-view (:calendars app)))))]]])))
