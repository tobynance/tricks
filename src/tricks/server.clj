(ns tricks.server
  (:require [clojure.tools.logging :as log]
            [tricks.cards]
            [clojure.java.shell :as shell])
  (:use [tricks.cards :only [in?]]))

(def MAX-SCORE 1000)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defprotocol process-protocol
  (process-read-line [process])
  (process-write-line [process text])
  (process-close [process]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord ClientProxy [name ^java.lang.Process process ^java.io.BufferedReader in ^java.io.BufferedWriter out cards score]
  process-protocol
  (process-read-line
    [p]
    (.readLine in))
  (process-write-line
    [p text]
    (.write out (.trim text))
    (.write out "\n")
    (.flush out))
  (process-close
    [p]
    (.destroy process)))

(defrecord Server [clients client-name-order n-hand n-trick])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn start-client-proxy
  "Executes a command-line program, returning a stdout stream and a
   stdin stream.  Takes a list of strings which represent the command
   arguments"
  [name args ^java.io.File dir]
  (let [process (.exec (Runtime/getRuntime) (reduce str (interleave args (iterate str " "))) nil dir)
        in (java.io.BufferedReader. (java.io.InputStreamReader. (.getInputStream process) (java.nio.charset.Charset/forName "UTF-8")))
        out (java.io.BufferedWriter. (java.io.OutputStreamWriter. (.getOutputStream process) (java.nio.charset.Charset/forName "UTF-8")))]
    (ClientProxy. name process in out [] 0)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn client-names
  [game-server]
  (:client-name-order @game-server))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn clients-in-order
  [game-server]
  (map #(get (:clients @game-server) %) (:client-name-order @game-server)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn broadcast-message
  [game-server message]
  (log/info "Broadcasting message: " message)
  (doseq [client (clients-in-order game-server)]
    (process-write-line client message)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn clients-to-client-map
  [clients]
  (zipmap (map :name clients) clients))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn update-play-order
  [game-server name]
  (when (not (= name (first (client-names game-server))))
    (let [[suffix prefix body] (partition-by #(= % name) (client-names game-server))]
      (reset! game-server (assoc @game-server :client-name-order (concat prefix body suffix))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn start-game
  [game-server]
  (log/info "Starting Game")
  (reset! game-server (-> @game-server
                          (assoc :n-hand 0)
                          (assoc :n-trick 0)
                          (update-in [:client-name-order] shuffle)))
  (let [names (clojure.string/join "|" (client-names game-server))
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
(defmacro time-limited [ms & body]
  `(let [f# (future ~@body)]
     (.get f# ~ms java.util.concurrent.TimeUnit/MILLISECONDS)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn query-client
  [client]
  (log/info (format "Querying client %s for a card" (:name client)))
  (process-write-line client "|QUERY|card|END|")
  (let [response (time-limited 2000 (process-read-line client))]
    (nth (.split response "\\|") 3)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn valid-card
  [card client leading-card]
  (cond
    (not (tricks.cards/valid-card card)) false
    (not (in? (:cards client) card)) false
    (nil? leading-card) true
    (= (second leading-card) (second card)) true
    (empty? (filter #(= % (second leading-card)) (map second (:cards client)))) true
    :else false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn play-tricks
  "Play tricks until we run out of cards or a player returns a bad card."
  [game-server]
  (if (not (empty? (:cards (first (clients-in-order game-server)))))
    (do
      (let [names (clojure.string/join "|" (client-names game-server))
            message (format "|INFO|start trick|%s|%s|END|" (:n-trick @game-server) names)]
        (broadcast-message game-server message))
      (let [played-cards
            (loop [clients (rest (clients-in-order game-server))
                   client (first (clients-in-order game-server))
                   played []]
              (let [card (query-client client)
                    leading-card (first played)]
                (if (valid-card card client leading-card)
                  (do
                    (broadcast-message game-server (format "|INFO|played|%s|%s|END|" (:name client) card))
                    (reset! game-server (update-in @game-server [:clients (:name client) :cards] (fn [cards] (remove #(= % card) cards))))
                    (let [new-played (conj played card)]
                      (if (empty? clients)
                        new-played
                        (recur (rest clients) (first clients) new-played))))
                  (do
                    (log/warn (format "Player %s sent bad card '%s'" (:name client) card))
                    (broadcast-message game-server (format "|INFO|bad card|%s|%s|END|" (:name client) card))
                    (reset! game-server (update-in @game-server [:clients (:name client) :score] #(- % 100)))
                    nil))))]
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
      (process-write-line client message)))

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
  [game-server]
  (let [score (high-score (clients-in-order game-server))]
     (when (< score MAX-SCORE)
       (play-hand game-server)
       (recur game-server))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn run
  [client-infos]
  (log/info "Server starting up...")
  (let [client-list (map #(apply start-client-proxy %) client-infos)
        client-names (shuffle (map :name client-list))
        clients (clients-to-client-map client-list)
        game-server (atom (Server. clients client-names 0 0))]
    (start-game game-server)
    (play-game game-server)
    (end-game game-server)
    (shutdown-agents)
    (log/info "Server shutting down...")))
