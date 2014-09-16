(ns tricks.server
  (:require [clojure.tools.logging :as log]
            [tricks.cards]
            [clojure.java.shell :as shell]
            [tricks.client-proxy :as client-proxy]
            [tricks.utils :refer [in? time-limited]]))

(defrecord Server [clients client-name-order n-hand n-trick])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn clients-in-order
  "Get the list of clients in play order"
  [game-server]
  (map #(get (:clients @game-server) %) (:client-name-order @game-server)))

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
  [game-server name]
  (when (not (= name (first (:client-name-order @game-server))))
    (let [[suffix prefix body] (partition-by #(= % name) (:client-name-order @game-server))]
      (reset! game-server (assoc @game-server :client-name-order (concat prefix body suffix))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn start-game
  [game-server]
  (log/info "Starting Game")
  (reset! game-server (-> @game-server
                          (assoc :n-hand 0)
                          (assoc :n-trick 0)
                          (update-in [:client-name-order] shuffle)))
  (let [names (clojure.string/join "|" (:client-name-order @game-server))
        message (str "|INFO|start game|" names "|END|")]
    (broadcast-message game-server message)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn end-game
  [game-server]
  (log/info "Ending Game")
  (let [scores
        (for [[name client] (:clients @game-server)]
          (format "%s %s" name (:score client)))
        score-text (clojure.string/join "|" scores)]
    (broadcast-message game-server (format "|INFO|end game|%s|END|" score-text))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn high-score
  "Get the highest score across the clients."
  [clients]
  (apply max (map :score clients)))

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
(defn play-trick
  [game-server clients current-client played]
  (let [client-name (:name current-client)
        card (query-client current-client)
        leading-card (first played)]
    (if (valid-card card current-client leading-card)
      (do
        (broadcast-message game-server (format "|INFO|played|%s|%s|END|" client-name card))
        (reset! game-server (update-in @game-server [:clients client-name :cards] (fn [cards] (remove #(= % card) cards))))
        (let [new-played (conj played card)]
          (if (empty? clients)
            new-played
            (recur game-server (rest clients) (first clients) new-played))))
      (do
        (log/warn (format "Player %s sent bad card '%s'" client-name card))
        (broadcast-message game-server (format "|INFO|bad card|%s|%s|END|" client-name card))
        (reset! game-server (update-in @game-server [:clients client-name :score] #(- % 100)))
        nil))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn play-tricks
  "Play tricks until we run out of cards or a player returns a bad card."
  [game-server]
  (if (not (empty? (:cards (first (clients-in-order game-server)))))
    (do
      (let [names (clojure.string/join "|" (:client-name-order @game-server))
            message (format "|INFO|start trick|%s|%s|END|" (:n-trick @game-server) names)]
        (broadcast-message game-server message))
      (let [played-cards (play-trick
                           game-server
                           (rest (clients-in-order game-server))
                           (first (clients-in-order game-server))
                           [])]
        ;;; all cards for trick are now played (or we got back a bad card,
        ;;; so figure out who won.
        (when (not (nil? played-cards))
          (let [winning-card (last (sort-by tricks.cards/face-value (filter #(tricks.cards/in-same-suit % (first played-cards)) played-cards)))
                winner-name (:name (nth (clients-in-order game-server) (.indexOf played-cards winning-card)))
                current-trick (:n-trick @game-server)]
            (reset! game-server (-> @game-server
                                    (update-in [:clients winner-name :score] inc)
                                    (update-in [:n-trick] inc)))
            (let [score (get-in @game-server [:clients winner-name :score])]
              (broadcast-message game-server (format "|INFO|end trick|%s|%s|%s|END|" current-trick winner-name score))
              (update-play-order game-server winner-name)
              (recur game-server))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn play-hand
  [game-server]
  (log/info "Starting hand " (:n-hand @game-server))
  (broadcast-message game-server (format "|INFO|start hand|%s|END|" (:n-hand @game-server)))


  ;;; initialize the deck and assign the cards to the players
  (let [deck (tricks.cards/shuffle-deck (tricks.cards/create-deck))
        hands (partition 13 deck)
        clients (clients-to-client-map (map
                                         (fn [client hand] (assoc client :cards hand))
                                         (clients-in-order game-server)
                                         hands))]
    ;;; map over the clients and associate a hand with each of them
    (reset! game-server
            (assoc @game-server
              :clients clients)))

  (doseq [client (clients-in-order game-server)]
    (let [cards (clojure.string/join " " (:cards client))
          message (format "|INFO|cards|%s|END|" cards)]
      (log/info (format "%s -> %s" (:name client) message))
      (client-proxy/process-write-line client message)))

  (play-tricks game-server)
  (broadcast-message game-server (format "|INFO|end hand|%s|END|" (:n-hand @game-server)))
  ;;; Update the game-server to the new hand #, n-trick to 0, and
  ;;; shuffle the client list
  (reset! game-server (-> @game-server
                          (update-in [:n-hand] inc)
                          (assoc :n-trick 0)
                          (update-in [:client-name-order] shuffle))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn play-game
  "play hands until a client scores 'max-score' points"
  [game-server max-score]
  (let [score (high-score (clients-in-order game-server))]
     (when (< score max-score)
       (play-hand game-server)
       (recur game-server max-score))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn run
  [client-infos max-score]
  (log/info "Server starting up...")
  (let [client-list (map #(apply client-proxy/start-client-proxy %) client-infos)
        client-names (shuffle (map :name client-list))
        clients (clients-to-client-map client-list)
        game-server (atom (Server. clients client-names 0 0))]
    (start-game game-server)
    (play-game game-server max-score)
    (end-game game-server)
    (shutdown-agents)
    (log/info "Server shutting down...")))
