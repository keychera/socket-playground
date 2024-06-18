(ns server
  (:require [clojure.core.async :refer [alt! chan close! go thread]]
            [world :refer [world-tick]])
  (:import [java.io
            BufferedReader
            InputStreamReader
            ObjectInputStream
            ObjectOutputStream
            PrintWriter]
           [java.net ServerSocket Socket]))

(defn client-handler [client-socket {:keys [on-kill]}]
  (let [killed-ch (chan)]
    (go
      (let [out (PrintWriter. (-> client-socket .getOutputStream) true)
            in  (ObjectInputStream. (-> client-socket .getInputStream))]
        (try
          (loop []
            (let [ops-ch (go (let [event (try (-> in .readObject)
                                              (catch Exception e (println "readLine interrupted" (.getMessage e))))
                                   result (world-tick event)]
                               (-> out (.println result))))]
              (alt!
                ops-ch    (recur)
                killed-ch :client-killed)))
          (finally
            ; closing out first before in, because closing in is blocking somehow
            ; https://stackoverflow.com/q/646940/8812880
            ; but I think the solution was to close the stream (need to investigate later)
            ; it was true https://stackoverflow.com/a/3596072/8812880
            ; but I think there are better solution
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
        (let [client-socket      (-> server-socket .accept)
              uuid               (random-uuid)
              _ (println "new client:" uuid)
              new-client-handler (client-handler client-socket {:on-kill (fn [] (swap! <connected-clients> #(dissoc % uuid)))})]
          (swap! <connected-clients> #(assoc % uuid new-client-handler)))))
    {:port port :<connected-clients> <connected-clients>
     :close #(do (doseq [[uuid client-handler] @<connected-clients>]
                   (println "killing client handler" uuid)
                   (.invoke (:kill client-handler)))
                 (reset! <is-running> false)
                 (-> server-socket .close))}))

(defn start-client [ip port]
  (let [client-socket (Socket. ip port)
        out           (ObjectOutputStream. (-> client-socket .getOutputStream))
        in            (BufferedReader. (InputStreamReader. (-> client-socket .getInputStream)))]
    {:port port
     :damage-boss (fn [damage]
                    (-> out (.writeObject {:type :damage-boss :data {:damage damage}}))
                    (-> in .readLine))
     :close #(some-> client-socket .close)}))

(comment
  (def server   (start-server 6666))
  (def client   (start-client "127.0.0.1" 6666))
  (def client-2 (start-client "127.0.0.1" 6666))

  @world/<world>

  (.invoke (:damage-boss client) 5)
  (.invoke (:damage-boss client-2) 7)

  (-> server :<connected-clients>)

  (.invoke (:close server))
  (.invoke (:close client))
  (.invoke (:close client-2)))
