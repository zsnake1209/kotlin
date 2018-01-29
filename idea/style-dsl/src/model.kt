package kotlinx.colorScheme

interface Call {
    val receiver: Receiver
    val declaration: MemberDeclaration
}

interface MemberDeclaration {
    val packageName: String
    val name: String
    val containingClass: Class?
}

interface Class {
    val fqName: String
    val annotations: List<Class>
}

//  TODO: useless abstraction
interface Receiver {
    val dispatchReceiver: Class?
    val extensionReceiver: Class?
}