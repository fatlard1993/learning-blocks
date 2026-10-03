package justfatlard.learning_blocks;

/**
 * One thing that must not take the server down with it: a tick's job, a room's tick, a narration
 * step. A failure is logged with what it was, and the caller decides what to drop; nothing escapes
 * into the server tick. Only the errors a JVM cannot recover from (out of memory) are let through;
 * a stack overflow (a word's sequence inside itself) is caught like anything else.
 */
public final class Safely {
	private Safely() {}

	/** Run it; false, logged, if it failed. */
	public static boolean run(String what, Runnable action) {
		try {
			action.run();
			return true;
		} catch (Throwable failure) {
			if (failure instanceof VirtualMachineError && !(failure instanceof StackOverflowError)) throw (VirtualMachineError) failure;
			LearningBlocks.LOGGER.error("[{}] {} failed", LearningBlocks.MOD_ID, what, failure);
			return false;
		}
	}
}
