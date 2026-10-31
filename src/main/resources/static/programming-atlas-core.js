// Pure teaching models. Never evaluate or compile visitor-supplied source code.
export const normalize = value => String(value || '').toLocaleLowerCase('ru').replaceAll('ё','е').trim();
export const missions = {
  backend: {ids:['java','csharp','kotlin','go','python','sql'],note:'Серверу нужны язык приложения, база данных и понимание сети. Java и SQL составляют один из распространённых вариантов; выбор зависит от существующей системы.'},
  web: {ids:['javascript','typescript','dart'],note:'В браузере основную роль играет JavaScript; TypeScript добавляет проверку типов. HTML и CSS отвечают за структуру и представление страницы.'},
  science: {ids:['fortran','matlab','r','python','julia','sql'],note:'Различайте статистический анализ, создание численного метода и обработку данных. Python, R и Julia помогают исследовать; Fortran и нативные библиотеки сохраняют важную вычислительную роль.'},
  systems: {ids:['machine','assembly','c','cplusplus','ada','rust','go'],note:'Здесь важны устройство памяти, границы ресурсов и конкретная платформа. C и ассемблер помогают увидеть фундамент, Rust предлагает другую модель контроля времени жизни.'},
  mobile: {ids:['java','kotlin','swift','dart','csharp'],note:'Сначала определите целевую платформу и библиотеки. Kotlin и Java связаны с Android, Swift с платформами Apple, Dart с Flutter; общий код не отменяет особенностей устройств.'},
  start: {ids:['scratch','basic','python','java'],note:'Можно начать с визуальных блоков, понятного сценария или последовательного курса Java. Важнее регулярная практика с переменными, условиями, циклами и маленькими проверяемыми задачами.'}
};
export function matchesLanguage(language, {query='',era='all',paradigm='all',use='all',mission=''}={}) {
  const text = normalize([language.name,language.people,language.idea,language.detail,language.year,...language.paradigms,...language.uses].join(' '));
  return (!normalize(query) || text.includes(normalize(query))) &&
    (era==='all' || language.era===Number(era)) &&
    (paradigm==='all' || language.paradigms.includes(paradigm)) &&
    (use==='all' || language.uses.includes(use)) &&
    (!missions[mission] || missions[mission].ids.includes(language.id));
}
export const machineProgram = [
  {address:0x0600,bytes:[0xd8],code:'CLD',note:'Выбираем двоичную арифметику: D = 0.'},
  {address:0x0601,bytes:[0xa9,0x02],code:'LDA #$02',note:'Загружаем число 2 в аккумулятор A.'},
  {address:0x0603,bytes:[0x18],code:'CLC',note:'Очищаем перенос: C = 0.'},
  {address:0x0604,bytes:[0x69,0x03],code:'ADC #$03',note:'Складываем 2 + 3 + C. Аккумулятор A = 5.'},
  {address:0x0606,bytes:[0x85,0x10],code:'STA $10',note:'Сохраняем 5 в ячейке памяти $0010.'},
  {address:0x0608,bytes:[0x00],code:'BRK',note:'Учебная остановка. На процессоре BRK вызывает программное прерывание.'}
];
export const hex = (value,width=2) => '$'+value.toString(16).toUpperCase().padStart(width,'0');
export function createMachineState(){ return {step:0,pc:0x0600,a:0,c:1,d:1,memory:0,halted:false}; }
export function stepMachine(state) {
  if(state.halted || state.step>=machineProgram.length)return state;
  const instruction=machineProgram[state.step],next={...state,step:state.step+1,pc:state.pc+instruction.bytes.length};
  switch(instruction.bytes[0]) {
    case 0xd8: next.d=0;break;
    case 0xa9: next.a=instruction.bytes[1];break;
    case 0x18: next.c=0;break;
    case 0x69: {
      const sum=state.a+instruction.bytes[1]+state.c;
      next.a=sum&255;next.c=sum>255?1:0;break;
    }
    case 0x85: next.memory=state.a;break;
    case 0x00: next.pc=state.pc+2;next.halted=true;break;
    default: throw new Error('Unknown instruction in the fixed teaching program');
  }
  return next;
}
export const additionalComparisons = [
  {id:'java-loop',name:'Java / явный цикл',dialect:'Java 21, полный пример',explain:'Порядок шагов и изменение total описаны явно. Объектно-ориентированный язык не запрещает процедурное вычисление.',code:`public class AtlasLoop {
    public static void main(String[] args) {
        int total = 0;
        for (int n = 1; n <= 5; n++)
            total += n * n;
        System.out.println(total);
    }
}`},
  {id:'java-stream',name:'Java / поток данных',dialect:'Java 21, полный пример',explain:'map задаёт преобразование, sum агрегирование. Это функциональный приём без отдельной предметной модели.',code:`import java.util.stream.IntStream;
public class AtlasStreams {
    public static void main(String[] args) {
        int total = IntStream.rangeClosed(1, 5)
            .map(n -> n * n).sum();
        System.out.println(total);
    }
}`}
];
export function comparisonChoices(languages) {
  const ids=['lisp','java','fortran','python','sql','javascript','c'];
  const entries=ids.map(id=>{const language=languages.find(l=>l.id===id);if(!language)throw new Error('Missing comparison '+id);return {...language,name:id==='java'?'Java / объект и операция':language.name};});
  return [...entries,...additionalComparisons];
}
