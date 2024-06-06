(ns main
  (:require [clojure.core.async :refer [thread]])
  (:import [java.io BufferedReader InputStreamReader PrintWriter]
           [java.net Socket ServerSocket]))

; learning socket from https://www.baeldung.com/a-guide-to-java-sockets
(defn start-greet-server [port]
  (let [server-socket   (ServerSocket. port)
        socket-resource (atom {})]
    {:port   port
     :thread (thread
               (let [client-socket (. server-socket accept) ; this is blocking
                     out           (PrintWriter. (. client-socket getOutputStream) true)
                     in            (BufferedReader. (InputStreamReader. (. client-socket getInputStream)))
                     greeting      (. in readLine)]
                 (reset! socket-resource {:client-socket client-socket
                                          :in            in
                                          :out           out})
                 (if (= "hello server" greeting)
                   (. out (println "hello client"))
                   (. out (println "unrecognized greeting")))))
     :close  (fn []
               (let [{:keys [client-socket in out]} @socket-resource]
                 (some-> in .close)
                 (some-> out .close)
                 (some-> client-socket .close))
               (-> server-socket .close))}))

(defn start-greet-client [ip port]
  (let [client-socket (Socket. ip port)
        out           (PrintWriter. (. client-socket getOutputStream) true)
        in            (BufferedReader. (InputStreamReader. (. client-socket getInputStream)))]
    {:client-socket client-socket :in in :out out
     :port port
     :send (fn [message]
             (. out (println message))
             (. in readLine))
     :close (fn []
              (some-> in .close)
              (some-> out .close)
              (some-> client-socket .close))}))

(comment 
  (def server (start-greet-server 6666)) 
  (def client (start-greet-client "127.0.0.1" 6666)) 

  (.invoke (:send client) "hello server")
  
  (.invoke (:close server)) 
  (.invoke (:close client)))