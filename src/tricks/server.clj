(ns tricks.server
  (:require [clojure.tools.logging :as log]
            [tricks.cards]
            [clojure.java.shell :as shell]
            [tricks.client-proxy :as client-proxy]
            [tricks.utils :refer [in? time-limited]]))

(defrecord Server [clients client-name-order n-hand n-trick played max-score])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn clients-in-order
  "Get the list of clients in play order"
  [game-server]
  (map #(get (:clients game-server) %) (:client-name-order game-server)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn server->string
  [game-server]
  (str "<Server:\n"
       (format "client-name-order\t%s\n" (:client-name-order game-server))
       (format "n-hand\t%s\n" (:n-hand game-server))
       (format "n-trick\t%s\n" (:n-trick game-server))
       (format "played\t%s\n" (:played game-server))
       "clients:\n"
       (apply str (for [client (clients-in-order game-server)]
                    (format "\t%s\t%s\t%s\n"
                            (:name client)
                            (:score client)
                            (pr-str (:cards client)))))
       "\n"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn broadcast-message
  "Send a message to every client"
  [game-server message]
  (log/info "Broadcasting message: " message)
  (client-proxy/broadcast-message (clients-in-order game-server) message))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn clients-to-client-map
  "Convert a list of clients into a map of clients, mapping the client name to the client"
  [clients]
  (zipmap (map :name clients) clients))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn update-play-order
  "Create a new order for the next trick, maintaining the clockwise
  order of the players but starting with the player named by 'name'"
  [client-name-order name]
  (if (= name (first client-name-order))
    client-name-order
    (let [[suffix prefix body] (partition-by #(= % name) client-name-order)]
      (concat prefix body suffix))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn high-score
  "Get the highest score across the clients."
  [game-server]
  (apply max (map :score (.values (:clients game-server)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn query-client
  "Ask the client what card they want to play, and parse the response"
  [client]
  (log/info (format "Querying client %s for a card" (:name client)))
  (client-proxy/process-write-line client "|QUERY|card|END|")
  (let [response (time-limited 2000 (client-proxy/process-read-line client))]
    (nth (.split response "\\|") 3)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn valid-card
  "Checks whether the played card is valid in the current context"
  [card client leading-card]
  (cond
    (not (tricks.cards/valid-card card)) false
    (not (in? (:cards client) card)) false
    (nil? leading-card) true
    (= (second leading-card) (second card)) true
    (empty? (filter #(= % (second leading-card)) (map second (:cards client)))) true
    :else false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn play-bad-card
  [game-server client-name card]
  (log/warn (format "Player %s sent bad card '%s'" client-name card))
  (broadcast-message game-server (format "|INFO|bad card|%s|%s|END|" client-name card))
  (-> game-server
      (update-in [:clients client-name :score] #(- % 100))
      (assoc :played nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn play-good-card
  [game-server client-name card]
  (broadcast-message game-server (format "|INFO|played|%s|%s|END|" client-name card))
  (-> game-server
      (update-in [:clients client-name :cards] (fn [cards] (remove #(= % card) cards)))
      (update-in [:played] #(conj % card))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn play-trick
  ([game-server]
   (play-trick game-server (clients-in-order game-server)))
  ([game-server clients]
   (if (empty? clients)
     game-server
     (let [current-client (first clients)
           client-name (:name current-client)
           card (query-client current-client)
           leading-card (first (:played game-server))]
       (if (valid-card card current-client leading-card)
         (recur (play-good-card game-server client-name card) (rest clients))
         (play-bad-card game-server client-name card))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn calculate-winner
  [game-server]
  (if (nil? (:played game-server))
    game-server
    (let [winning-card (tricks.cards/winning-card (:played game-server))
          winner-name (:name (nth (clients-in-order game-server) (.indexOf (:played game-server) winning-card)))
          current-trick (:n-trick game-server)
          winning-score (inc (get-in game-server [:clients winner-name :score]))
          new-play-order (update-play-order (:client-name-order game-server) winner-name)]
      (broadcast-message game-server (format "|INFO|end trick|%s|%s|%s|END|" current-trick winner-name winning-score))
      (-> game-server
          (update-in [:n-trick] inc)
          (assoc-in [:clients winner-name :score] winning-score)
          (assoc :client-name-order new-play-order)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn play-tricks
  "Play tricks until we run out of cards or a player returns a bad card."
  [game-server]
  ;(log/info (server->string game-server))
  (if (empty? (:cards (first (clients-in-order game-server))))
    game-server
    (do
      (let [names (clojure.string/join "|" (:client-name-order game-server))
            message (format "|INFO|start trick|%s|%s|END|" (:n-trick game-server) names)]
        (broadcast-message game-server message))
      (-> game-server
          (assoc :played [])
          play-trick
          calculate-winner
          recur))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn start-hand
  [game-server]
  (log/info "Starting hand " (:n-hand game-server))
  (broadcast-message game-server (format "|INFO|start hand|%s|END|" (:n-hand game-server)))


  ;;; initialize the deck and assign the cards to the players
  (let [deck (tricks.cards/shuffle-deck (tricks.cards/create-deck))
        hands (partition 13 deck)
        clients (clients-to-client-map (map
                                         (fn [client hand] (assoc client :cards hand))
                                         (clients-in-order game-server)
                                         hands))]

    (doseq [client (.values clients)]
      (let [cards (clojure.string/join " " (:cards client))
            message (format "|INFO|cards|%s|END|" cards)]
        (log/info (format "%s -> %s" (:name client) message))
        (client-proxy/process-write-line client message)))

    ;;; map over the clients and associate a hand with each of them
    (assoc game-server :clients clients)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn end-hand
  [game-server]
  (broadcast-message game-server (format "|INFO|end hand|%s|END|" (:n-hand game-server)))
  ;;; Update the game-server to the new hand #, n-trick to 0, and
  ;;; shuffle the client list
  (-> game-server
      (update-in [:n-hand] inc)
      (assoc :n-trick 0)
      (update-in [:client-name-order] shuffle)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn play-game
  "play hands until a client scores 'max-score' points"
  [game-server]
  (let [score (high-score game-server)]
    (if (> score (:max-score game-server))
      game-server
      (-> game-server
          start-hand
          play-tricks
          end-hand
          recur))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn start-game
  [client-infos max-score]
  (log/info "Starting Game")
  (let [client-list (map #(apply client-proxy/start-client-proxy %) client-infos)
        client-names (shuffle (map :name client-list))
        clients (clients-to-client-map client-list)
        names (clojure.string/join "|" client-names)
        message (str "|INFO|start game|" names "|END|")
        game-server (Server. clients client-names 0 0 [] max-score)]
    (broadcast-message game-server message)
    game-server))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn end-game
  [game-server]
  (log/info "Ending Game")
  (let [scores
        (for [[name client] (:clients game-server)]
          (format "%s %s" name (:score client)))
        score-text (clojure.string/join "|" scores)]
    (broadcast-message game-server (format "|INFO|end game|%s|END|" score-text))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn run
  [client-infos max-score]
  (log/info "Server starting up...")
  (-> (start-game client-infos max-score)
      (play-game)
      end-game)
  (shutdown-agents)
  (log/info "Server shutting down..."))
