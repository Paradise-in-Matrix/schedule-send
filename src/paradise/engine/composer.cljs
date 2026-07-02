(ns paradise.engine.composer
  (:require [cljs.core.async :refer [go]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [cljs-workers.worker :as worker]
            [clojure.string :as str]
            [paradise.engine.state :as state]
            [net :as net]))

(worker/register :send-delayed-event
                 (fn [{:keys [room-id content formatted-body delay-ms]}]
                   (go
                     (try
                       (let [client  @state/!client
                             session (.session client)
                             token   (.-accessToken session)
                             hs      (str/replace (.-homeserverUrl session) #"/+$" "")
                             txn-id  (str "delay-" (.getTime (js/Date.)) "-" (rand-int 10000))
                             url     (str hs "/_matrix/client/v3/rooms/" (js/encodeURIComponent room-id)
                                          "/send/m.room.message/" txn-id
                                          "?org.matrix.msc4140.delay=" delay-ms)
                             payload {:msgtype        "m.text"
                                      :body           content
                                      :format         "org.matrix.custom.html"
                                      :formatted_body formatted-body}
                             resp (<p! (net/fetch url  {:method  "PUT"
                                                        :headers #js {"Authorization" (str "Bearer " token)
                                                                      "Content-Type"  "application/json"}
                                                        :body    (js/JSON.stringify (clj->js payload))}))]
                           (if (.-ok resp)
                             {:status :success}
                             {:status :error :msg (.-status resp)}))
                       (catch :default e {:status :error :msg (str e)})))))


