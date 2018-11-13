package org.sonarsource.slang

import java.io.{File, PrintWriter}

import scala.io.{Source => ioSource}
import scala.meta._
import scala.collection.mutable.ListBuffer
import scala.meta.Mod.Override

object App {
  val ruleReportOutput = "native-rules/out/scala-S138.json"
  val currentRule: Tree => List[Integer] = funLen

  def main(args: Array[String]): Unit = {
    def recursiveListFiles(f: File): Array[File] = {
      val these = f.listFiles
      these ++ these.filter(_.isDirectory).flatMap(recursiveListFiles)
    }

    val src = recursiveListFiles(new File("its\\\\sources\\\\scala"))

    val issues = src.filter(f => f.isFile && f.getName.endsWith(".scala")).map{file => // .take(20)
      (file.toString, issueInFile(file))}.filter(_._2.nonEmpty).toMap

    report(issues)
  }

  def issueInFile(file: File): List[Integer] = {
    ioSource.fromFile(file).getLines.mkString("\n").parse[Source] match {
      case Parsed.Success(tree) => {
        currentRule(tree)
      }
      case Parsed.Error(pos, _, _) =>
        println("Unable to parse file content.");
        Nil
    }
  }

  // Rule implementation ===============================================================================================
  def funParamNumber(t: Tree): List[Integer] = {
    val max = 7

    def visit(tree: Tree): List[Integer] = tree match {
      case defn: Defn.Def if defn.paramss.size <= 1 && !defn.mods.contains(Override()) =>
        val childIssues: List[Integer] = defn.children.flatMap(visit)

        if(defn.paramss.flatten.size > max) {
          childIssues.::(defn.pos.startLine + 1)
        } else {
          childIssues
        }
      case _  => if(tree.children.isEmpty) List() else tree.children.flatMap(visit)
    }

    visit(t)
  }

  //Naive fun len https://jira.sonarsource.com/browse/RSPEC-138
  def funLen(t: Tree): List[Integer] = {
    val max = 100

    def visit(tree: Tree): List[Integer] = tree match {
      case defn: Defn.Def if !defn.mods.contains(Override()) =>
        val childIssues: List[Integer] = defn.children.flatMap(visit)
        if(defn.pos.endLine-defn.pos.startLine > max){
          childIssues.::(defn.pos.startLine + 1)
        } else {
          childIssues
        }
      case _  => if(tree.children.isEmpty) List() else tree.children.flatMap(visit)
    }

    visit(t)
  }

  def funLenSlangWay(t: Tree): List[Integer] = {
    val max = 100

    def visit(tree: Tree): List[Integer] = tree match {
      case defn: Defn.Def if defn.paramss.size <= 1 && !defn.mods.contains(Override()) =>
        val childIssues: List[Integer] = defn.children.flatMap(visit)
        if(defn.pos.endLine-defn.pos.startLine > max){
          childIssues.::(defn.pos.startLine + 1)
        } else {
          childIssues
        }
      case _  => if(tree.children.isEmpty) List() else tree.children.flatMap(visit)
    }

    visit(t)
  }


  def matchLen(t: Tree): List[Integer] = {
    //https://jira.sonarsource.com/browse/RSPEC-1479
    val max = 30

    def visit(tree: Tree): List[Integer] = tree match {
      case m: Term.Match =>
        val childIssues: List[Integer] = m.children.flatMap(visit)

        if(m.cases.size > max){
          childIssues.::(m.pos.startLine + 1)
        } else {
          childIssues
        }
      case _  => if(tree.children.isEmpty) List() else tree.children.flatMap(visit)
    }

    visit(t)
  }

  def matchLenSlangWay(t: Tree): List[Integer] = {
    //https://jira.sonarsource.com/browse/RSPEC-1479
    val max = 30

    def visit(tree: Tree): List[Integer] = tree match {
      case m: Term.Match if !m.cases.exists(c => c.cond.nonEmpty) =>
        val childIssues: List[Integer] = m.children.flatMap(visit)

        if(m.cases.size > max){
          childIssues.::(m.pos.startLine + 1)
        } else {
          childIssues
        }
      case _  => if(tree.children.isEmpty) List() else tree.children.flatMap(visit)
    }

    visit(t)
  }



  def report(issues : Map[String, List[Integer]]) = {
    val pw = new PrintWriter(new File(ruleReportOutput))
    pw.write(JsonConverter.toJson(issues))
    pw.close
  }
}

object JsonConverter {
  def toJson(o: Any) : String = {
    var json = new ListBuffer[String]()
    o match {
      case m: Map[_,_] => {
        for ( (k,v) <- m ) {
          var key = escape(k.asInstanceOf[String])
          v match {
            case a: Map[_,_] => json += "\"" + key + "\":" + toJson(a)
            case a: List[_] => json += "\"" + key + "\":" + toJson(a)
            case a: Int => json += "\"" + key + "\":" + a
            case a: Boolean => json += "\"" + key + "\":" + a
            case a: String => json += "\"" + key + "\":\"" + escape(a) + "\""
            case _ => ;
          }
        }
      }
      case m: List[_] => {
        var list = new ListBuffer[String]()
        for ( el <- m ) {
          el match {
            case a: Map[_,_] => list += toJson(a)
            case a: List[_] => list += toJson(a)
            case a: Int => list += a.toString()
            case a: Boolean => list += a.toString()
            case a: String => list += "\"" + escape(a) + "\""
            case _ => ;
          }
        }
        return "[\n" + list.mkString("", ",\n", ",\n") + "]"
      }
      case _ => ;
    }
    return "{\n" + json.mkString("",",\n", ",\n") + "}\n"
  }

  private def escape(s: String) : String = {
    return s.replaceAll("\"" , "\\\\\"");
  }
}
