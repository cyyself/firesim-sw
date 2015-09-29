package strober

import Chisel._
import scala.collection.mutable.{ArrayBuffer, HashMap}
import scala.io.Source

class Replay[+T <: Module](c: T, matchFile: Option[String] = None, isTrace: Boolean = true) extends Tester(c, isTrace) {
  private val basedir = Driver.targetDir
  private val signalMap = HashMap[String, Node]()
  private val samples = ArrayBuffer[Sample]()

  def loadSamples(filename: String) {
    samples += new Sample
    val lines = scala.io.Source.fromFile(basedir+"/"+filename).getLines
    var forced = false
    for (line <- lines) {
      val tokens = line split " "
      val cmd = SampleInstType(tokens.head.toInt)
      cmd match {
        case SampleInstType.FIN => samples += new Sample
        case SampleInstType.LOAD =>
          val value = BigInt(tokens.init.last, 16)
          val off = tokens.last.toInt
          (signalMap get tokens.tail.head) match {
            case None =>
            case Some(node) => 
              samples.last addCmd Load(node, value, if (off < 0) None else Some(off))
          }
        case SampleInstType.FORCE => 
          val node = signalMap(tokens.tail.head)
          val value = BigInt(tokens.last, 16)
          samples.last addCmd Force(node, value)
          forced = true
        case SampleInstType.POKE => 
          val node = signalMap(tokens.tail.head).asInstanceOf[Bits]
          val value = BigInt(tokens.last, 16)
          samples.last addCmd PokePort(node, value)
        case SampleInstType.STEP => 
          if (!forced || !Driver.isInlineMem) samples.last addCmd Step(tokens.last.toInt)
          forced = false
        case SampleInstType.EXPECT => 
          val node = signalMap(tokens.tail.head).asInstanceOf[Bits]
          val value = BigInt(tokens.last, 16)
          samples.last addCmd ExpectPort(node, value)
      }
    }
  }

  private val matchMap = matchFile match {
    case None => Map[String, String]()
    case Some(f) => {
      val lines = scala.io.Source.fromFile(f).getLines
      (lines map { line =>
        val tokens = line split " "
        tokens.head -> tokens.last
      }).toMap
    }
  }

  def loadWires(node: Node, value: BigInt, off: Option[Int]) {
    def loadsram(path: String, v: BigInt, off: Int) {
      (matchMap get path) match {
        case None => // skip
        case Some(p) => pokePath("%s.memory[%d]".format(p, off), v) 
      }
    }
    def loadff(path: String, v: BigInt) {
      (matchMap get path) match {
        case None => // skip
        case Some(p) => pokePath(p, v)
      }
    }
    node match {
      case mem: Mem[_] if mem.seqRead =>
        loadsram(dumpName(mem), value, off.get)
      case _ if (node.needWidth == 1) => 
        val path = dumpName(node) + (off map ("[" + _ + "]") getOrElse "") 
        loadff(path, value)
      case _ => (0 until node.needWidth) foreach { idx =>
        val path = dumpName(node) + (off map ("[" + _ + "]") getOrElse "") + "[" + idx + "]"
        loadff(path, (value >> idx) & 0x1)
      }
    }
  } 

  def run {
    val startTime = System.nanoTime
    samples foreach (_ map {
      case Step(n) => step(n)
      case Force(node, value) => node match {
        case mem: Mem[_] if mem.seqRead => if (!Driver.isInlineMem) {
          pokePath("%s.sram.A1".format(dumpName(mem)), value, true)
          pokePath("%s.sram.WEB1".format(dumpName(mem)), BigInt(1), true)
        } else {
          pokeNode(findSRAMRead(mem)._1, value)
        }
        case _ => // Todo
      }
      case Load(node, value, off) => matchFile match {
        case None => node match {
          case mem: Mem[_] if mem.seqRead && !Driver.isInlineMem =>
            pokePath("%s.sram.memory[%d]".format(dumpName(mem), off.get), value)
          case _ => 
            pokeNode(node, value, off)
        }
        case Some(f) => loadWires(node, value, off)
      }
      case PokePort(node, value) => poke(node, value)
      case ExpectPort(node, value) => expect(node, value)
    })
    val endTime = System.nanoTime
    val simTime = (endTime - startTime) / 1000000000.0
    val simSpeed = t / simTime
    println("Time elapsed = %.1f s, Simulation Speed = %.2f Hz".format(simTime, simSpeed))
  }

  Driver.dfs { node =>
    if (node.isReg || node.isIo) signalMap(node.chiselName) = node
  }
  loadSamples(c.name + ".sample")
  run
}
