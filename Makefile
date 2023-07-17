PLATFORM ?= xilinx_vcu118
init:
	git submodule update --init

dependencies/riscv-isa-sim/build/libfesvr.a:
	(mkdir dependencies/riscv-isa-sim/build || true) && cd dependencies/riscv-isa-sim/build && ../configure && make -j `nproc`

dependencies/dromajo/src/libdromajo_cosim.a:
	make -C dependencies/dromajo/src

out/build/FireSim-generated.const.h: ../playground/rtl/FireSim-generated.const.h
	mkdir -p out/build
	cp ../playground/rtl/FireSim-generated.const.h out/build/FireSim-generated.const.h

out/FireSim-$(PLATFORM): out/build/FireSim-generated.const.h dependencies/riscv-isa-sim/build/libfesvr.a dependencies/dromajo/src/libdromajo_cosim.a
	make -C sim/midas/src/main/cc driver MAIN=$(PLATFORM) PLATFORM=$(PLATFORM) \
		CXXFLAGS="-I$(PWD)/sim/firesim-lib/src/main/cc -isystem $(PWD)/dependencies/testchipip/src/main/resources/testchipip/csrc -I$(PWD)/dependencies/dromajo/src/ -I$(PWD)/dependencies/riscv-isa-sim/ -I$(PWD)/dependencies/riscv-isa-sim/build/" \
		LDFLAGS="-ldwarf -lelf" \
		DRIVER_NAME=FireSim \
		GEN_FILE_BASENAME=FireSim-generated \
		GEN_DIR=$(PWD)/out/build \
		OUT_DIR=$(PWD)/out \
		DRIVER="$(PWD)/sim/src/main/cc/firesim/firesim_top.cc $(PWD)/sim/firesim-lib/src/main/cc/bridges/blockdev.cc $(PWD)/sim/firesim-lib/src/main/cc/bridges/dromajo.cc $(PWD)/sim/firesim-lib/src/main/cc/bridges/groundtest.cc $(PWD)/sim/firesim-lib/src/main/cc/bridges/simplenic.cc $(PWD)/sim/firesim-lib/src/main/cc/bridges/tracerv.cc $(PWD)/sim/firesim-lib/src/main/cc/bridges/tsibridge.cc $(PWD)/sim/firesim-lib/src/main/cc/bridges/uart.cc $(PWD)/sim/firesim-lib/src/main/cc/fesvr/firesim_tsi.cc $(PWD)/sim/firesim-lib/src/main/cc/bridges/tracerv/tracerv_dwarf.cc $(PWD)/sim/firesim-lib/src/main/cc/bridges/tracerv/tracerv_elf.cc $(PWD)/sim/firesim-lib/src/main/cc/bridges/tracerv/tracerv_processing.cc $(PWD)/sim/firesim-lib/src/main/cc/bridges/tracerv/trace_tracker.cc $(PWD)/dependencies/riscv-isa-sim/build/libfesvr.a $(PWD)/dependencies/dromajo/src/libdromajo_cosim.a $(PWD)/dependencies/testchipip/src/main/resources/testchipip/csrc/testchip_tsi.cc"

clean:
	rm -rf out