(ns ui
  (:require [clojure.core.async :refer [thread alt!! timeout chan close!]]
            [reitit.ring :as ring]
            [ring.adapter.jetty9 :as jetty]
            [ring.websocket :as ringws]
            [selmer.parser :refer [render-file]]
            [hiccup.core :refer [html]]))

(def <counter> (atom 0))
(def <ws-clients> (atom {}))

(defn game-html [_req]
  {:body (render-file "game.html" {:counter @<counter>})})

(defn req->ws-key [req]
  (-> req :headers (get "sec-websocket-key")))

(defn game-ws-handler [req]
  (let [provided-subprotocols (:websocket-subprotocols req)]
    {:ring.websocket/listener {:on-open  (fn [socket]
                                        ;;    (tap> [:ws :open socket])
                                           (swap! <ws-clients> assoc (req->ws-key req) #:ws{:socket socket}))
                               
                               :on-close (fn [socket status-code reason]
                                        ;;    (tap> [:ws :close socket status-code reason])
                                           (swap! <ws-clients> dissoc (req->ws-key req)))}
     :ring.websocket/protocol (first provided-subprotocols)}))

(defn game-ws [req]
  (if (jetty/ws-upgrade-request? req)
    (game-ws-handler req)
    {:body "nothing here!"}))

(def routes
  (ring/router
   [""
    ["/"        {:get game-html}]
    ["/ws/game" {:get game-ws}]]))

(def handler (ring/ring-handler routes))

(defn create-game-server [port]
  (jetty/run-jetty handler {:port port :join? false}))

(defn create-counter-thread []
  (let [kill-switch-ch (chan)]
    (thread (loop []
              (alt!!
                (timeout 1000) (do (let [new-value (swap! <counter> inc)]
                                     (println "counting" new-value)
                                     (doseq [[_ client] @<ws-clients>]
                                       (ringws/send (:ws/socket client) (html [:div#counter new-value]))))
                                   (recur))
                kill-switch-ch (println "killed counter thread"))))
    kill-switch-ch))

(comment
  (add-tap (bound-fn* clojure.pprint/pprint))
  (add-tap #(def last-tapped-value %))
  last-tapped-value
  
  (def game-server (create-game-server 4242)) 
  (some-> game-server .stop)

  @<counter>
  (swap! <counter> inc)

  @<ws-clients>
  (doseq [[_ client] @<ws-clients>] 
    (ringws/send (:ws/socket client) (html [:div#counter @<counter>])))

  (def counter-thread (create-counter-thread))
  (close! counter-thread))