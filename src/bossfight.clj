(ns bossfight
  (:require [clojure.core.async :refer [thread go alt! chan close!]])
  (:import [java.io BufferedReader InputStreamReader PrintWriter]
           [java.net Socket ServerSocket]))

(defn client-handler [client-socket {:keys [on-kill]}]
  (let [killed-ch (chan)]
    (go
      (let [out (PrintWriter. (. client-socket getOutputStream) true)
            in  (BufferedReader. (InputStreamReader. (. client-socket getInputStream)))]
        (try
          (loop []
            (let [ops-ch (go (let [action (try (. in readLine)
                                               (catch Exception e (println "readLine interrupted" (.getMessage e))))]
                               (. out (println (str "doing " action)))))]
              (alt!
                ops-ch    (recur)
                killed-ch :client-killed)))
          (finally
            ; closing out first before in, because closing in is blocking somehow
            ; https://stackoverflow.com/q/646940/8812880
            ; but I think the solution was to close the stream (need to investigate later)
            (some-> out .close)
            (some-> in .close)
            (on-kill)))))
    {:kill #(close! killed-ch)}))

(defn start-server [port]
  (let [server-socket (ServerSocket. port)
        <is-running>        (atom true)
        <connected-clients> (atom {})]
    (thread
      (while @<is-running>
        (let [client-socket      (. server-socket accept)
              uuid               (random-uuid)
              _ (println "new client:" uuid)
              new-client-handler (client-handler client-socket {:on-kill (fn [] (swap! <connected-clients> #(dissoc % uuid)))})]
          (swap! <connected-clients> #(assoc % uuid new-client-handler)))))
    {:port port :<connected-clients> <connected-clients>
     :close #(do (doseq [[uuid client] @<connected-clients>]
                   (println "killing client handler" uuid)
                   (.invoke (:kill client)))
                 (reset! <is-running> false)
                 (-> server-socket .close))}))

(defn start-client [ip port]
  (let [client-socket (Socket. ip port)
        out           (PrintWriter. (. client-socket getOutputStream) true)
        in            (BufferedReader. (InputStreamReader. (. client-socket getInputStream)))]
    {:client-socket client-socket :in in :out out
     :port port
     :send (fn [message]
             (. out (println message))
             (. in readLine))
     :close #(do (some-> in .close)
                 (some-> out .close)
                 (some-> client-socket .close))}))

(comment
  (def server   (start-server 6666))
  (def client   (start-client "127.0.0.1" 6666))
  (def client-2 (start-client "127.0.0.1" 6666))

  (.invoke (:send client) "stuff")
  (.invoke (:send client-2) "something")

  (-> server :<connected-clients>)

  (.invoke (:close server))
  (.invoke (:close client))
  (.invoke (:close client-2)))
