package lang24.phase.asmdump;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import lang24.data.asm.*;
import lang24.data.lin.LinDataChunk;
import lang24.data.mem.MemTemp;
import lang24.phase.*;
import lang24.phase.asmgen.*;
import lang24.phase.imclin.ImcLin;
import lang24.phase.livean.LiveAn;
import lang24.phase.livean.LivenessAnalyzer;
import lang24.phase.regall.RegAll;

/**
 * Asm file dumping.
 */
public class AsmDump extends Phase {

	private String fileName;

	public AsmDump(String name) {
		super("asmdump");
		this.fileName = name;
	}

	private String DATA_SEGMENT = """
				LOC Data_Segment
				GREG Stack_Segment	//STACK POINTER
				GREG Stack_Segment	//FRAME POINTER
				GREG Pool_Segment	//HEAP POINTER
				GREG @				//BASE ADDRESS FOR DATA

	PADDING			BYTE 0,0,0,0,0,0,0
	BUFFER			BYTE 0,0
	ARGS			OCTA BUFFER
				OCTA 2

							//INTEGER TO PRINT
	INTEGER			BYTE 0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0

	""";

	private String LIBRARY = """
	// READS A SINGLE CHARACTER FROM STDIN
	_getchar	LDA	$255,ARGS
			TRAP 0,Fgets,StdIn
			LDA $0,PADDING
			LDO $0,$0,0
			STO $0,$254,0		//SAVE CHAR TO STACK
			POP 0,0
	
	// PRINTS A SINGLE CHARACTER TO STDOUT
	_putchar	LDO $0,$254,8		//GET FROM STACK
			LDA $1,PADDING		//SL IS AT OFFSET 0
			STO $0,$1,0			//FIRST ARG AT OFFSET 8
			LDA $255,BUFFER
			TRAP 0,Fputs,StdOut
			POP 0,0
	
	// DOES NOTHING
	_del		POP 0,0
	
	// EXITSTHE PROGRAM WITH STATUS
	_exit		LDO $255,$254,8
			TRAP 0,Halt,0
	
	// ALLOCATES MEMORY ON THE HEAP
	_new		LDO $0,$254,8		//GET FROM STACK
			MUL $0,$0,8
			STO	$252,$254,0		//SAVE RESULT TO STACK
			ADD $252,$252,$0
			POP 0,0
	
	// PRINTS A INT TO STDOUT
	_putint	GET $0,rJ			//SAVE RETURN ADDRESS 
			SUB $1,$254,8		//SO WE CAN CALL GETCHAR
			STO $0,$1,0
			LDO $0,$254,8
			LDA $1,INTEGER
			ADD $1,$1,20
			CMP $255,$0,0
			BN $255,__minus
	
	__itoa		SUB $1,$1,1
			DIV $0,$0,10
	
			GET $255,rR
			ADD $255,$255,'0'
			STB $255,$1,0
			CMP $255,$0,0
			BNZ $255,__itoa
	
	__print		ADD $255,$1,0
			TRAP 0,Fputs,StdOut
			SUB $1,$254,8
			LDO $1,$1,0
			PUT rJ,$1
			POP 0,0
	
	__minus	SETL $255,'-'
			STO $255,$254,8
			PUSHJ $2,_putchar
			NEG $0,$0
			JMP __itoa
	
	// READ A INT FROM CONSOLE
	_getint		GET $0,rJ
			SUB $1,$254,8		//SAVE RETURN ADDRESS SO WE CAN CALL
			STO $0,$1,0			//GETCHAR FUNCTION
			SETL $0,0
			JMP __ws
	
	__read		PUSHJ $2,_getchar
	__skip		LDO $255,$254,0
			CMP $1,$255,'0'
			BN $1,__end
			CMP $1,$255,'9'
			BP $1,__end
			MUL $0,$0,10
			SUB $255,$255,'0'
			ADD $0,$0,$255
			JMP __read
	
	__end		SUB $1,$254,8		//RESTORE RETURN ADDRESS
			LDO $1,$1,0
			PUT rJ,$1
			STO $0,$254,0
			POP 0,0
	
	__ws		PUSHJ $2,_getchar
			LDO $255,$254,0
			CMP $1,$255,'-'
			BZ $1,__readNeg
			CMP $1,$255,'0'
			BN $1,__ws
			CMP $1,$255,'9'
			BP $1,__ws
			JMP __skip
	
	__readNeg	PUSHJ $2,_getchar
			LDO $255,$254,0
			CMP $1,$255,'0'
			BN $1,__endNeg
			CMP $1,$255,'9'
			BP $1,__endNeg
			MUL $0,$0,10
			SUB $255,$255,'0'
			ADD $0,$0,$255
			JMP __readNeg
	
	__endNeg	SUB $1,$254,8		//RESTORE RETURN ADDRESS
			LDO $1,$1,0
			PUT rJ,$1
			NEG $0,$0
			STO $0,$254,0
			POP 0,0

	""";

	private String CODE_SEGMENT = """
				LOC #100
	""";

	private String PROLOGUE = """
	%s			SETL $0,%d			//PROLOGUE
				NEG $0,$0
				STO $253,$254,$0
				SUB $0,$0,8
				GET $1,rJ
				STO $1,$254,$0
				ADD $253,$254,0
				SETL $1,%d
				SUB $254,$254,$1
				JMP %s
	""";

	private String EPILOGUE = """
	%s			STO %s,$253,0		//EPILOGUE
				SETL $0,%d
				NEG $0,$0
				LDO $1,$253,$0
				PUT rJ,$1
				ADD $254,$253,0
				ADD $0,$0,8
				LDO $253,$253,$0

	""";

	public void dump() {
		String outputFileName = fileName + ".mms";
		try (FileWriter fileWriter = new FileWriter(outputFileName)) {
            
			writeStaticData(fileWriter);
			writeFunctions(fileWriter);
			fileWriter.write(LIBRARY);
			
        } catch (IOException e) {
            System.err.println("An error occurred while writing asm file: " + e.getMessage());
        }
	}

	private void writeStaticData(FileWriter fileWriter) {
		try {
			fileWriter.write(DATA_SEGMENT);
			for (LinDataChunk data : ImcLin.dataChunks()) {
				fileWriter.write(data.label.name + "			OCTA ");
				if (data.init != null) {
					for (int i = 0; i < data.size/8; i++) {
						fileWriter.write((int) data.init.charAt(i + 1) + ((i < data.size/8 - 1) ? "," : "\n"));
					}
				} else {
					fileWriter.write("0\n");
					fileWriter.write(String.format("			LOC %s+%d\n", data.label.name, data.size));
				}
				fileWriter.write("\n");
			}
		} catch (IOException e) {
		System.err.println("An error occurred while writing data to asm file: " + e.getMessage());
		}
	}

	private void writeFunctions(FileWriter fileWriter) {
		try {
			fileWriter.write(CODE_SEGMENT);
			for (Code code : AsmGen.codes) {
				fileWriter.write("\n");
				String funLabel = code.frame.label.name.equals("_main") ? "Main" : code.frame.label.name;
				long FPOffset = code.frame.locsSize + 8;
				long RAOffset = code.frame.locsSize + 16;
				String entryLabel = code.entryLabel.name;
				String exitLabel = code.exitLabel.name;
				String returnValueReg = "$" + RegAll.tempToReg.get(code.frame.RV);
				long frameSize = code.frame.size + code.tempSize;

				String myPrologue = String.format(PROLOGUE, funLabel, FPOffset, frameSize, entryLabel);
				String myEpilogue = String.format(EPILOGUE, exitLabel, returnValueReg, RAOffset);

				fileWriter.write(myPrologue);
				for (int i = 0; i < code.instrs.size(); i++) {
					AsmInstr asm = code.instrs.get(i);
					if (asm instanceof AsmLABEL) {
						if (code.instrs.get(i + 1) instanceof AsmLABEL)
							fileWriter.write(asm.toString(RegAll.tempToReg) + "			ADD $1,$1,0\n");
						else
							fileWriter.write(asm.toString(RegAll.tempToReg));
					} else {
						fileWriter.write("			" + asm.toString(RegAll.tempToReg) + "\n");
					}
				}

				fileWriter.write(myEpilogue);

				if (funLabel.equals("Main")) {
					fileWriter.write("			LDO $255,$254,0\n");
					fileWriter.write("			TRAP 0,Halt,0\n\n");
				}else {
					fileWriter.write("			POP 0,0");
				}
				
			}
		} catch (IOException e) {
		System.err.println("An error occurred while writing functions to asm file: " + e.getMessage());
		}
	}

}
