(ns world)

(def <world> (atom {:boss-hp 100
                    :players-hp []}))

(defn world-tick [{:keys [type data] :as event}]
  (case type
    :damage-boss   (let [{:keys [damage]} data]
                     (swap! <world> #(update % :boss-hp - damage))
                     (str "damage boss with " damage " damage"))
    :damage-player (let [{:keys [damage idx]} data]
                     (swap! <world> #(update-in % [:players-hp idx] - damage))
                     (str "damage player #" idx " with " damage " damage"))
    (str "nothing happened on: " event)))