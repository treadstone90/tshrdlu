package tshrdlu.twitter

/**
 * Copyright 2013 Karthik Padmanabhan, Nazneen Rajani, and Nick Wilson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import twitter4j._
import scala.io.Source
import collection.JavaConversions._
import scala.collection.mutable.ArrayBuffer

/**
 * A direct message replier that only responds to "are you alive?".
 */
class AliveDirectMessageReplier(master: BotMaster) extends DirectMessageReplier {
  def attemptDirectMessageReply(directMessage: DirectMessage): Option[String] = {
    if (directMessage.getText.matches("""(?i).*\bare you alive\?.*""")) Some("yes") else None
  }
}


/**
 * A default replier when the bot has nothing interesting to say.
 */
class DefaultReplier(master: BotMaster)
extends StatusReplier with DirectMessageReplier {

  lazy val defaultReplies = Vector(
    "That does not interest me",
    "I really don't care about that",
    "Can we talk about something else?",
    "Let's talk about something else")

  def attemptStatusReply(status: Status): Option[String] = {
    return getReply(status.getText)
  }

  def attemptDirectMessageReply(directMessage: DirectMessage): Option[String] = {
    return getReply(directMessage.getText)
  }

  private def getReply(text: String): Option[String] = {
    return Some(defaultReplies(scala.util.Random.nextInt(defaultReplies.length)))
  }

}


/**
 * A replier for status and direct messages. Responds to requests to make
 * sandwiches.
 *
 * @see <a href="http://xkcd.com/149/">http://xkcd.com/149/</a>
 */
class SudoReplier(master: BotMaster)
extends StatusReplier with DirectMessageReplier {

  lazy val MakeSandwichRE = """(?i)(?:.*(\bsudo\b))?.*\bmake (?:me )?an?\b.*\bsandwich\b.*""".r

  def attemptStatusReply(status: Status): Option[String] = {
    return getReply(status.getText)
  }

  def attemptDirectMessageReply(directMessage: DirectMessage): Option[String] = {
    return getReply(directMessage.getText)
  }

  private def getReply(text: String): Option[String] = {
    val reply = text match {
      case MakeSandwichRE(sudo) => {
        if (sudo == null) Some("What? Make it yourself.") else Some("Okay.")
      }
      case _ => None
    }

    return reply
  }
}


  class WisdomReplier(master:BotMaster)
  extends StatusReplier with DirectMessageReplier 
  {

    import tshrdlu.util.SimpleTokenizer
    import collection.JavaConversions._

    lazy val stopwords = Source.fromFile("stopwords").getLines().toSet

   // Recognize a follow command
    lazy val FollowRE = """(?i)(?<=follow)(\s+(me|@[a-z_0-9]+))+""".r

  // Pull just the lead mention from a tweet.
    lazy val StripLeadMentionRE = """(?:)^@[a-z_0-9]+\s(.*)$""".r

  // Pull the RT and mentions from the front of a tweet.
    lazy val StripMentionsRE = """(?:)(?:RT\s)?(?:(?:@[a-z]+\s))+(.*)$""".r

    val userList = userSearch("quote",master.twitter)


  def userSearch(searchWith:String, twitter:Twitter) :ArrayBuffer[String] = 
  {
    var userList = ArrayBuffer[(String,Int)]()
    println("Initializing Wisdom Replier");
    for(page<- 1 to 30)
    {
      print(100*page*1.0/30 + "%....." ) 
      val temp = master.twitter.searchUsers(searchWith,page);
      userList = userList ++ temp.map(x=> (x.getScreenName,x.getFollowersCount))
    }

    val a = userList.sortBy(x=>x._2).reverse.map(x=> x._1).slice(0,20)
    println(a);
    a

  }


  def attemptStatusReply(status:Status) :Option[String] ={
    return getReply(status.getText)
  }

  def attemptDirectMessageReply(directMessage:DirectMessage) : Option[String] = 
  {
    return getReply(directMessage.getText)
  }

  private def getReply(s:String) : Option[String] = 
  {
    val text = s.toLowerCase

    
      try {
        val StripLeadMentionRE(withoutMention) = text
        val statusList = 
        SimpleTokenizer(withoutMention)
        .filter(_.length > 3)
        .filter(_.length < 10)
        .filterNot(_.contains('/'))
        .filter(tshrdlu.util.English.isSafe)
        .sortBy(- _.length)
        .toSet
        .take(3)
        .toList//.flatMap(w => twitter.search(new Query(w)).getTweets)
        // the one above is a list of tokens
        val candidateTweets = userList.map(x=> master.twitter.search(new Query("from:"+x)).getTweets).flatten;
        
        println("candidtae Tweets size is " + candidateTweets.length);
        
        val candidateTweetsText = candidateTweets.map(x=> x.getText).toSeq
        val reply = generateReply(candidateTweetsText,statusList)
        Some(reply)

        //extractText(statusList)
      }
      catch { 
        case  e : Throwable => println(e);None
      }
    }
    
  


  def generateReply(statusList:Seq[String],tweet : Seq[String]) = {

    val processedCandidates = statusList.map(x=> normalize(x))

    val filterCandidates:Seq[String] = processedCandidates.map {
      case StripMentionsRE(rest) => rest
      case x => x
    }
    .filterNot(_.contains('/'))
    .filterNot(_.contains('@'))
    .filter(tshrdlu.util.English.isSafe) 
    .filter(tshrdlu.util.English.isEnglish)



    //println("filtered candidiates" + filterCandidates)


    val potentialResponse= filterCandidates.map(x=> (x,score(x,tweet)) ).sortBy(x=>x._2).reverse(0)

    if(potentialResponse._2 == 0) "I pass" else potentialResponse._1;

  }


  
  def score(x: String , tweet:Seq[String])=
  {
    val candidate = SimpleTokenizer(x).filter(x=> stopwords.contains(x) == false).toSet
    


    val score = tweet.map(x=> if(candidate.contains(x)) 1 else 0 ).sum
    println("size is  " + candidate.size + "and score is " + score);
    score
  }

  def normalize(sentence:String):String  = {
    val normalizedSentence = if(sentence.startsWith("\"")) (sentence.split("\""))(1) 
    else if(sentence.startsWith("\'")) (sentence.split("\'"))(1)
    else (sentence.split("-"))(0)
    


    normalizedSentence

  }

}

