(ns ui
  (:require [clojure.core.async :refer [thread alt!! timeout chan close!]]
            [reitit.ring :as ring]
            [ring.adapter.jetty9 :refer [run-jetty]]
            [selmer.parser :refer [render-file]]))

(def counter (atom 0))

(defn game-html [req]
  {:body (render-file "game.html" {:counter @counter})})

(def routes
  (ring/router
   [""
    ["/" {:get game-html}]]))

(def handler (ring/ring-handler routes))

(defn create-game-server [port]
  (run-jetty handler {:port port :join? false}))

(defn create-counter-thread []
  (let [kill-switch-ch (chan)]
    (thread (loop [] 
              (println "counting" (swap! counter inc))
              (alt!! 
                (timeout 1000) (do (println "counting" (swap! counter inc))
                                   (recur))
                kill-switch-ch (println "killed counter thread"))))
    kill-switch-ch))

(comment
  (def game-server (create-game-server 4242))
  (some-> game-server .stop)

  @counter

  (swap! counter inc)

  (def counter-thread (create-counter-thread))
  (close! counter-thread))