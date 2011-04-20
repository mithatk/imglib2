package script.imglib.math;

import script.imglib.math.fn.IFunction;
import script.imglib.math.fn.UnaryOperation;
import mpicbg.imglib.img.Img;
import mpicbg.imglib.type.numeric.RealType;

public class Cos extends UnaryOperation {

	public Cos(final Img<? extends RealType<?>> img) {
		super(img);
	}
	public Cos(final IFunction fn) {
		super(fn);
	}
	public Cos(final Number val) {
		super(val);
	}

	@Override
	public final double eval() {
		return Math.cos(a().eval());
	}
}