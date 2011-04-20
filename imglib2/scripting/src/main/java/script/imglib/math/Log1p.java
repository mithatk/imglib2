package script.imglib.math;

import script.imglib.math.fn.IFunction;
import script.imglib.math.fn.UnaryOperation;
import mpicbg.imglib.img.Img;
import mpicbg.imglib.type.numeric.RealType;

/** Returns the natural logarithm of the sum of the argument and 1. */
public class Log1p extends UnaryOperation {

	public Log1p(final Img<? extends RealType<?>> img) {
		super(img);
	}
	public Log1p(final IFunction fn) {
		super(fn);
	}
	public Log1p(final Number val) {
		super(val);
	}

	@Override
	public final double eval() {
		return Math.log1p(a().eval());
	}
}