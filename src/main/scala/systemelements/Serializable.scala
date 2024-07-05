package systemelements

trait Serializable(val code: String):
  override def equals(obj: Any): Boolean = obj match
    case elem: Serializable => elem.code == code
    case _ => false  
