(ns ui
  (:require-macros [utils.macros :refer [defoverride]])
  (:require [client.state :as state]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [taoensso.timbre :as log]
            [cljs.core.async :refer [go <!]]
            [cljs-workers.core :as workers]
            [utils.svg :as svg]
            [input.composer :as composer]
            [utils.global-ui :as global-ui]))

(defn current-local-datetime []
  (let [now    (js/Date.)
        offset (* (.getTimezoneOffset now) 60000)
        local  (js/Date. (- (.getTime now) offset))]
    (subs (.toISOString local) 0 16)))

(rf/reg-event-fx
 :message/schedule
 (fn [_ [_ room-id content schedule-time formatted-body]]
   (let [target-time (.getTime (js/Date. schedule-time))
         now         (js/Date.now)
         delay-ms    (max 0 (- target-time now))
         pool        @state/!engine-pool]
     (go
       (let [res (<! (workers/do-with-pool! pool
                                            {:handler :send-delayed-event
                                             :arguments {:room-id        room-id
                                                         :content        content
                                                         :formatted-body formatted-body
                                                         :delay-ms       delay-ms}}))]
         (if (= (:status res) "success")
           (log/info "Delayed event scheduled successfully via worker")
           (log/error "Worker failed to schedule event:" (:msg res)))))
     {})))

(defn scheduler-modal-content [{:keys [room-id content formatted-body clear-fn]}]
  (r/with-let [!state   (r/atom {:schedule-time (current-local-datetime)})
               close-fn #(rf/dispatch [:ui/close-modal])]
    (let [{:keys [schedule-time]} @!state]
      [:div.scheduler-modal
       [:h3.scheduler-title "Schedule Message"]
       [:div.scheduler-body
        [:label.scheduler-label "Select Date and Time:"]
        [:input.scheduler-input
         {:type "datetime-local"
          :value schedule-time
          :on-change #(swap! !state assoc :schedule-time (.. % -target -value))}]]
       [:div.scheduler-actions
        [:button.cancel-btn
         {:on-click close-fn}
         "Cancel"]
        [:button.submit-btn
         {:disabled (empty? schedule-time)
          :on-click (fn []
                      (rf/dispatch [:message/schedule room-id content schedule-time formatted-body])
                      (when clear-fn (clear-fn))
                      (close-fn))}
         "Schedule Send"]]])))

(defoverride timeline-send-button [{:keys [submit-message! editor attachments]}]
  (let [active-room-id @(rf/subscribe [:rooms/active-id])]
    [:button.timeline-send-btn
     {:on-click (fn [e]
                  (.preventDefault e)
                  (.stopPropagation e)
                  (submit-message!))
      :on-context-menu (fn [e]
                         (.preventDefault e)
                         (.stopPropagation e)
                         (when editor
                           (let [body (.getText editor)
                                 formatted-body (composer/get-matrix-formatted-body editor)]
                             (rf/dispatch [:ui/open-modal :plugin-portal
                                           {:render-fn scheduler-modal-content
                                            :room-id active-room-id
                                            :content body
                                            :formatted-body formatted-body
                                            :backdrop-props {:class "lightbox-backdrop"}
                                            :window-props   {:style {:background "transparent"
                                                                     :box-shadow "none"
                                                                     :padding "0"
                                                                     :border "none"}}}]))))}
     [svg/send]]))
