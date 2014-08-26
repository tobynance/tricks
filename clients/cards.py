import random, collections

#######################################################################
#######################################################################
class Card(object):
    def __init__(self, face, suit):
        assert(suit in "HSDC")
        self.suit = suit
        self.face = face

        if face not in "TJQKA":
            val = int(self.face)
            assert(1 < val < 11)

    ###################################################################
    def __eq__(self, other):
        if isinstance(other, Card):
            return self.suit == other.suit and self.face == other.face
        return False

    ###################################################################
    def __ne__(self, other):
        return not (self == other)

    ###################################################################
    def __hash__(self):
        x = [str(ord(c)) for c in str(self)]
        return int("".join(x))

    ###################################################################
    @classmethod
    def fromString(cls, text):
        return Card(text[:-1], text[-1])

    ###################################################################
    def getFaceValue(self):
        if self.face == "T":
            return 10
        elif self.face == "J":
            return 11
        elif self.face == "Q":
            return 12
        elif self.face == "K":
            return 13
        elif self.face == "A":
            return 14
        else:
            val = int(self.face)
            return val

    ###################################################################
    def __str__(self):
        return "%s%s" % (self.face, self.suit)

#######################################################################
def getCardsFromString(text):
    cards = [Card(x[:-1], x[-1]) for x in text.split()]
    return cards

#######################################################################
def getStringFromCards(cards):
    return " ".join([str(c) for c in cards])

#######################################################################
#######################################################################
class Hand(object):
    def __init__(self, cards=None):
        if cards:
            self.cards = set(cards)
        else:
            self.cards = set()

    ###################################################################
    @classmethod
    def fromString(cls, text):
        return Hand(getCardsFromString(text))

    ###################################################################
    def __iter__(self):
        return iter(self.cards)

    ###################################################################
    def remove(self, card):
        return self.cards.remove(card)

    ###################################################################
    def add(self, card):
        return self.cards.add(card)

    ###################################################################
    def getCardsOfSuit(self, suit):
        output = []
        for card in self.cards:
            if card.suit == suit:
                output.append(card)
        return output

    ###################################################################
    def __eq__(self, other):
        return self.cards == other.cards
    
    ###################################################################
    def __ne__(self, other):
        return not (self == other)

    ###################################################################
    def __str__(self):
        return getStringFromCards(self.cards)

#######################################################################
#######################################################################
class Deck(object):
    def __init__(self):
        pass

    ###################################################################
    def shuffle(self):
        self._newSet()
        random.shuffle(self.cards)

    ###################################################################
    def deal13(self):
        hand = Hand()
        for i in range(13):
            hand.add(self.cards.pop())
        return hand

    ###################################################################
    def _newSet(self):
        self.cards = []
        for suit in "HSDC":
            for face in "A23456789JQK":
                self.cards.append(Card(face, suit))
            self.cards.append(Card("10", suit))

    ###################################################################
    def __str__(self):
        return " ".join([str(c) for c in self.cards])

#######################################################################
#######################################################################
class PresetDeck(Deck):
    def __init__(self, filename):
        Deck.__init__(self)
        self.input_file = file(filename)

    ###################################################################
    def shuffle(self):
        line = self.input_file.readline().strip()
        self.cards = getCardsFromString(line)
