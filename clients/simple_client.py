import sys
import logging
from cards import Card

#######################################################################
def setupLogging(name):
    logging.basicConfig(level=logging.DEBUG,
                        format='%(asctime)s: %(levelname)-8s: %(message)s',
                        filename='logger_%s.log' % name,
                        filemode='w')


#######################################################################
class StupidClient(object):
    def __init__(self, name):
        self.name = name
        self.cards = []
        self.first_card_of_trick = None

    ###################################################################
    def send_line(self, line):
        sys.stdout.write(line.rstrip())
        sys.stdout.write("\n")
        sys.stdout.flush()

    ###################################################################
    def handle_line(self, line):
        line = line.strip()
        logging.info("handle_line: '%s'", line)
        if not line.endswith("|END|"):
            logging.error("Unknown incoming line: %s", line)
            assert(line.endswith("|END|"))
        if line.startswith("|QUERY|"):
            return self.handle_query(line)
        elif line.startswith("|INFO|"):
            return self.handle_info(line)
        else:
            logging.error("Unknown incoming line: %s", line)
            return False

    ###################################################################
    def handle_query(self, line):
        logging.info("StupidClient: query: %s" % line)
        if line.startswith("|QUERY|card|END|"):
            if self.first_card_of_trick is None:
                ### we are playing the first card of the trick, so play whatever
                card = self.cards[0]
            else:
                same_suited_cards = self.get_cards_of_suit(self.first_card_of_trick.suit)
                if len(same_suited_cards) > 0:
                    card = same_suited_cards[0]
                else:
                    card = self.cards[0]
            self.cards.remove(card)
            logging.info("Sending response -> |RESPONSE|card|%s|END|" % str(card))
            self.send_line("|RESPONSE|card|%s|END|" % str(card))
        else:
            logging.error("Unknown incoming line: %s", line)
            return False
        return True

    ###################################################################
    def get_cards_of_suit(self, suit):
        output = []
        for card in self.cards:
            if card.suit == suit:
                output.append(card)
        return output

    ###################################################################
    def handle_info(self, line):
        logging.info("info: '%s'", line)
        if line.startswith("|INFO|end game|"):
            logging.info("Received 'end game' message")
            return False
        elif line.startswith("|INFO|start game|"):
            return True
        elif line.startswith("|INFO|start hand|"):
            return True
        elif line.startswith("|INFO|end hand|"):
            return True
        elif line.startswith("|INFO|start trick|"):
            self.first_card_of_trick = None
        elif line.startswith("|INFO|end trick|"):
            return True
        elif line.startswith("|INFO|bad card|"):
            return True
        elif line.startswith("|INFO|played|"):
            ### if this is the first card of the trick, figure out what the suit is
            if self.first_card_of_trick is None:
                self.first_card_of_trick = Card.fromString(line.split("|")[4])
                logging.info("Got first card of trick: %s", self.first_card_of_trick)
        elif line.startswith("|INFO|cards|"):
            card_text = line.split("|")[3]
            logging.info("card_text:%s" % card_text)
            self.cards = [Card.fromString(x) for x in card_text.split()]
        else:
            logging.error("Unknown incoming line: %s", line)
            return False
        return True


#######################################################################
def main():
    try:
        name = sys.argv[1]
        setupLogging(name)
        logging.info("Starting up for %s", name)
        client = StupidClient(name)
        while True:
            line = sys.stdin.readline()
            if client.handle_line(line) == False:
                logging.info("handle_line() returned False, exiting...")
                return
    except Exception, e:
        logging.exception(e)


#######################################################################
if __name__ == "__main__":
    main()