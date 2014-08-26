# Introduction to tricks

## Description

This is a card game, similar to Spades or Hearts, but with no twists to it.  You
could consider it the parent game that Spades and Hearts come from.

I wrote this as part of a polyglot contest, which allows players to write their client in any programming language, and to put it into the clients folder and connect it by modifying src/tricks/core.clj to use their client.  I currently only have the Python client enabled, but you can switch the Clojure into use by compiling it and adding it to the clients (and commenting out one of the Python clients).

## Goal

The goal of the game is to win as many tricks as you can.

## Order of Play
* 13 cards are dealt to each of the 4 players from a shuffled deck of 52 cards.
* The server specifies the order of the players.  After each hand the first
  player becomes the last player, and the second player becomes the first.
* Every other player must play in the same suit as the first player in the trick.
  If a player does not have a card in the same suit, then they can play whatever
  card they wish.
* The highest card of the same suite as the first player of the trick wins.
  The winner gains one point.
* The winner of the trick is then the first player of the next trick.
* After 13 tricks (and all cards have been played), the deck is reshuffled, and
  play starts over, but with all of the points still aggregating.
* A game stops as soon as a player reaches 1000 points.

## Terms

<dl>
<dt>Trick</dt>
<dd>A round of cards, where one card is played by each player, with the winner
being the high card in the same suit as the card that started the trick.</dd>

<dt>Hand</dt>
<dd>A set of 13 tricks, which plays through an entire deck.</dd>

<dt>Bad Card</dt>
<dd>Either a card not in the player's hand, or one which the player cannot
legally play at the moment (such as trying to play a Heart when they have a
card of the same suit as the first card in the trick).</dd>

<dt>Scoring</dt>
<dd>The winner of a trick scores 1 point.</dd>
</dl>

## Bad Card

If a player ever returns a bad card then the player has 100 points removed from
their score, the deck is reshuffled and re-dealt, and the game proceeds as
usual.

## Change in Rules

It may become necessary to clarify, or possibly even change these rules during
the development of the game.  I will work to inform everyone of any changes, with
an explanation of why they changed.

## Cheating

Cheating is not allowed, and it is up to the discretion of the judges to declare
what constituted cheating, meaning that even if it isn't explicitly disallowed,
if the judges think it is cheating, then you will be removed from the competition.
Some (non-exhaustive) examples of cheating would be attempting to read the server
logs, attempting to locate or read or modify any of the opponent programs, or
attempting to figure out what cards an opponent has through means other than the
messages sent from the server to your client (and the logic you apply to those
messages).

## Communication

The server will provide communication with all of the clients via standard in
and standard out.  Player name is passed to the client program as a command line
argument.

Cards are specified as the card face, as one of 1-9, T, J, K, Q, or A, and the card
suit, as one of S, C, H, D.  For example:

<dl>
<dt>8C</dt>
<dd>8 of Clubs</dd>
<dt>TD</dt>
<dd>10 of Diamonds</dd>
<dt>QH</dt>
<dd>Queen of Hearts</dd>
<dt>AS</dt>
<dd>Ace of Spades</dd>
<dt>KH</dt>
<dd>King of Hearts</dd>
</dl>

All communication will follow the following format:

    |MESSAGE_TYPE|MESSAGE_KEY|DATA|END|

There is a newline after the last bar after END.

Data can have multiple fields in it, or not be included at all.

    +----------------------------------------------------------------------------------------+
    | MESSAGE_TYPE                                                                           |
    +===============+==================+=====================================================+
    | INFO          | Server to Client | Informs client of something.  No response expected. |
    +---------------+------------------+-----------------------------------------------------+
    | QUERY         | Server to Client | Asks client something.  Expects Response.           |
    +---------------+------------------+-----------------------------------------------------+
    | RESPONSE      | Client to Server | Client response to query.                           |
    +---------------+------------------+-----------------------------------------------------+

    +----------------------------------------------------------------------------------------+
    | MESSAGE_KEYS - QUERY                                                                   |
    +======+=================================================================================+
    | card | Asks client what card they wish to play.                                        |
    |      | The client will respond with the 'card' message.                                |
    +------+---------------------------------------------------------------------------------+

Here is the only possible form of the card query::

    |QUERY|card|END|

    +----------------------------------------------------------------------------------------+
    | MESSAGE_KEYS - RESPONSE                                                                |
    +======+=================================================================================+
    | card | The client responds with the card they wish to play                             |
    +------+---------------------------------------------------------------------------------+

Here are some example responses:

    |RESPONSE|card|6H|END|
    |RESPONSE|card|QS|END|
    |RESPONSE|card|AC|END|
    |RESPONSE|card|TD|END|

Each card must be in the player's hand, or they are penalized according to the 'bad card'
rule listed above.

    +----------------------------------------------------------------------------------------+
    | MESSAGE_KEYS - INFO                                                                    |
    +=============+==========================================================================+
    | start game  | Informs all clients of the start of the game. Provides the list of       |
    |             | players for the data part of the message as separate fields.             |
    +-------------+--------------------------------------------------------------------------+
    | end game    | Informs all clients of the end of the game. Provides the list of players |
    |             | with their ending scores.                                                |
    +-------------+--------------------------------------------------------------------------+
    | cards       | Informs a single client of the hand they have been dealt. Provides the   |
    |             | list of cards as a single data field.                                    |
    +-------------+--------------------------------------------------------------------------+
    | start hand  | Informs all clients of the beginning of a new hand, providing the number |
    |             | of the hand.                                                             |
    +-------------+--------------------------------------------------------------------------+
    | end hand    | Informs all clients of the end of a hand, providing the number of the    |
    |             | hand.                                                                    |
    +-------------+--------------------------------------------------------------------------+
    | start trick | Informs all clients of the start of a trick. Provides the number of the  |
    |             | trick, counting upwards throughout the hand, plus the names of the       |
    |             | players in order for the hand.                                           |
    +-------------+--------------------------------------------------------------------------+
    | end trick   | Informs all clients of the end of a trick. Provides the number of the    |
    |             | trick, the winner of the trick, and the number of points awarded to the  |
    |             | winner of the trick.                                                     |
    +-------------+--------------------------------------------------------------------------+
    | played      | Informs all clients of what a player played. Provides the name and the   |
    |             | card played.                                                             |
    +-------------+--------------------------------------------------------------------------+
    | bad card    | Informs all clients that a player has played a bad card. Provides the    |
    |             | name and the card played.                                                |
    +-------------+--------------------------------------------------------------------------+

Example Info:

    |INFO|start game|Jerry|Mitch|Cory|Jimbo|END|
    |INFO|end game|Jerry 100|Mitch 20|Cory 83|Jimbo 3|END|
    |INFO|cards|6H 7D AC KS KC 5H 2H 4C 4H 8D 2D 3D 4D|END|
    |INFO|start hand|2|END|
    |INFO|end hand|2|END|
    |INFO|start trick|11|Jerry|Mitch|Cory|Jimbo|END|
    |INFO|end trick|11|Mitch|6|END|
    |INFO|played|Hank|KS|END|
    |INFO|bad card|Hank|floober|END|
    |INFO|bad card|Jimbo|QS|END|
