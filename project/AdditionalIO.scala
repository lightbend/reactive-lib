import scala.sys.process._

object AdditionalIO {
  def runProcess(args: String*): Unit = {
    val code = args.!

    assert(code == 0, s"Executing $args yielded $code, expected 0")
  }
}
